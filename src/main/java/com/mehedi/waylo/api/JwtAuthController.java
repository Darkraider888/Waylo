package com.mehedi.waylo.api;

import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "waylo.jwt.enabled", havingValue = "true")
public class JwtAuthController {

    public record TokenRequest(@Email @NotBlank String email, @NotBlank String password) { }
    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) { }
    public record MeResponse(Long id, String fullName, String role) { }

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder encoder;
    private final UserRepository users;

    public JwtAuthController(AuthenticationManager authenticationManager,
                             JwtEncoder encoder, UserRepository users) {
        this.authenticationManager = authenticationManager;
        this.encoder = encoder;
        this.users = users;
    }

    @PostMapping("/auth/token")
    public TokenResponse token(@Valid @RequestBody TokenRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password()));
        User user = users.findByEmail(email).filter(User::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("waylo")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .subject(user.getEmail())
                .claim("role", user.getRole().name())
                .claim("authVersion", user.getAuthVersion())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String jwt = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(jwt, "Bearer", 900);
    }

    @GetMapping("/me")
    public MeResponse me(@org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        User user = users.findByEmail(jwt.getSubject()).filter(User::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return new MeResponse(user.getId(), user.getFullName(), user.getRole().name());
    }
}
