package com.mehedi.waylo.service;

import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.repository.PasswordResetTokenRepository;
import com.mehedi.waylo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

class PasswordResetEmailProviderTests {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ResendEmailSender resend = mock(ResendEmailSender.class);
    private final BrevoEmailSender brevo = mock(BrevoEmailSender.class);

    private PasswordResetService service(String provider) {
        var service = new PasswordResetService(
                users, tokens, passwordEncoder, resend, brevo, provider);
        ReflectionTestUtils.setField(service, "publicUrl", "https://waylo.example.test");
        return service;
    }

    private void setupAccount() {
        User user = new User();
        user.setEmail("test@example.test");
        user.setFullName("Test Traveler");
        when(users.findByEmail("test@example.test")).thenReturn(Optional.of(user));
    }

    @Test
    void brevoSelectedMeansOnlyBrevoIsCalled() {
        setupAccount();
        service("brevo").requestReset("TEST@example.test");
        verify(brevo).sendPasswordReset(eq("test@example.test"), eq("Test Traveler"),
                startsWith("https://waylo.example.test/reset-password?token="), eq(15));
        verifyNoInteractions(resend);
    }

    @Test
    void resendRemainsTheWorkingDefault() {
        setupAccount();
        service("resend").requestReset("test@example.test");
        verify(resend).sendPasswordReset(eq("test@example.test"), eq("Test Traveler"),
                startsWith("https://waylo.example.test/reset-password?token="), eq(15));
        verifyNoInteractions(brevo);
    }

    @Test
    void invalidProviderIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> service("smtpx"));
    }
}
