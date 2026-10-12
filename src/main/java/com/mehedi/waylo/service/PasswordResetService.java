package com.mehedi.waylo.service;

import com.mehedi.waylo.entity.PasswordResetToken;
import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.repository.PasswordResetTokenRepository;
import com.mehedi.waylo.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class PasswordResetService {

    private static final int TOKEN_VALIDITY_MINUTES = 15;

    private static final String PASSWORD_PATTERN =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).+$";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ResendEmailSender emailSender;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${waylo.public-url}")
    private String publicUrl;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            ResendEmailSender emailSender) {

        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
    }

    // =====================================================
    // 1. GENERATE SECURE RESET TOKEN
    // =====================================================

    private String generateToken() {

        byte[] randomBytes = new byte[32];

        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    // =====================================================
    // 2. HASH TOKEN BEFORE STORING IN DATABASE
    // =====================================================

    private String hashToken(String token) {

        try {

            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException ex) {

            throw new IllegalStateException(
                    "SHA-256 algorithm is unavailable",
                    ex
            );
        }
    }

    // =====================================================
    // 3. REQUEST PASSWORD RESET
    // =====================================================

    @Transactional
    public void requestReset(String email) {

        if (email == null || email.isBlank()) {
            return;
        }

        String normalizedEmail = email.trim()
                .toLowerCase(Locale.ROOT);

        Optional<User> optionalUser =
                userRepository.findByEmail(normalizedEmail);

        // Do not reveal whether an email is registered.
        if (optionalUser.isEmpty()) {
            return;
        }

        User user = optionalUser.get();

        if (!user.isEnabled()) {
            return;
        }

        // Invalidate previous reset links.
        tokenRepository.invalidateTokensForUser(user);

        String rawToken = generateToken();

        PasswordResetToken resetToken =
                new PasswordResetToken();

        resetToken.setUser(user);
        resetToken.setTokenHash(hashToken(rawToken));

        resetToken.setExpiresAt(
                LocalDateTime.now()
                        .plusMinutes(TOKEN_VALIDITY_MINUTES)
        );

        resetToken.setUsed(false);

        tokenRepository.saveAndFlush(resetToken);

        String resetLink =
                publicUrl.replaceAll("/+$", "")
                        + "/reset-password?token="
                        + rawToken;

        emailSender.sendPasswordReset(
                user.getEmail(),
                user.getFullName(),
                resetLink,
                TOKEN_VALIDITY_MINUTES
        );
    }

    // =====================================================
    // 4. VALIDATE RESET TOKEN
    // =====================================================

    @Transactional(readOnly = true)
    public boolean isTokenValid(String token) {

        if (token == null ||
                !token.matches("[A-Za-z0-9_-]{43}")) {

            return false;
        }

        String hashedToken = hashToken(token);

        return tokenRepository
                .findByTokenHash(hashedToken)
                .filter(reset -> !reset.isUsed())
                .filter(reset ->
                        reset.getExpiresAt()
                                .isAfter(LocalDateTime.now())
                )
                .filter(reset -> reset.getUser().isEnabled())
                .isPresent();
    }

    // =====================================================
    // 5. RESET USER PASSWORD
    // =====================================================

    @Transactional
    public void resetPassword(
            String token,
            String newPassword) {

        if (!isStrongPassword(newPassword)) {

            throw new IllegalArgumentException(
                    "Password must contain 8 to 72 characters, "
                            + "including uppercase, lowercase and numbers"
            );
        }

        if (token == null ||
                !token.matches("[A-Za-z0-9_-]{43}")) {

            throw new IllegalArgumentException(
                    "Invalid or expired reset link"
            );
        }

        String hashedToken = hashToken(token);

        // Lock the token to prevent simultaneous reuse.
        PasswordResetToken resetToken =
                tokenRepository.findForUpdate(hashedToken)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid or expired reset link"
                                )
                        );

        if (resetToken.isUsed() ||
                !resetToken.getExpiresAt()
                        .isAfter(LocalDateTime.now())) {

            throw new IllegalArgumentException(
                    "Invalid or expired reset link"
            );
        }

        User user = resetToken.getUser();

        if (!user.isEnabled()) {

            throw new IllegalArgumentException(
                    "Account is unavailable"
            );
        }

        String hashedPassword =
                passwordEncoder.encode(newPassword);

        user.setPassword(hashedPassword);
        user.setAuthVersion(user.getAuthVersion() + 1);

        userRepository.save(user);

        // Invalidate all outstanding reset tokens.
        tokenRepository.invalidateTokensForUser(user);
    }

    // =====================================================
    // 6. VALIDATE PASSWORD STRENGTH
    // =====================================================

    private boolean isStrongPassword(String password) {

        return password != null
                && password.length() >= 8
                && password.length() <= 72
                && password.getBytes(StandardCharsets.UTF_8).length <= 72
                && password.matches(PASSWORD_PATTERN);
    }
}
