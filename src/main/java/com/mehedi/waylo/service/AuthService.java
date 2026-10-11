package com.mehedi.waylo.service;

import com.mehedi.waylo.dto.RegisterRequest;
import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.enums.Role;
import com.mehedi.waylo.repository.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void register(RegisterRequest request) {

        if (request.getPassword() == null || request.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");

        String email = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (!request.getPassword()
                .equals(request.getConfirmPassword())) {

            throw new IllegalArgumentException(
                    "Passwords do not match"
            );
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists"
            );
        }

        User user = new User();

        user.setFullName(request.getFullName().trim());
        user.setEmail(email);

        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );

        // Public registration always creates a MEMBER.
        user.setRole(Role.MEMBER);
        user.setEnabled(true);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Also handle simultaneous registrations
            // for the same email.
            throw new IllegalArgumentException(
                    "Registration could not be completed. "
                            + "The email may already be registered.",
                    ex
            );
        }
    }
}
