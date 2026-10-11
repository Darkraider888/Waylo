package com.mehedi.waylo.controller;

import com.mehedi.waylo.service.PasswordResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordResetController {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetController.class);

    private final PasswordResetService resetService;

    public PasswordResetController(PasswordResetService resetService) {
        this.resetService = resetService;
    }

    @GetMapping("/forgot-password")
    public String forgotPage() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String requestReset(
            @RequestParam String email,
            Model model) {

        if (email == null || email.isBlank()
                || email.length() > 150) {
            model.addAttribute("error", "Enter a valid email address");
            return "auth/forgot-password";
        }

        try {
            resetService.requestReset(email);
        } catch (Exception ex) {
            // No email addresses, passwords, or reset tokens in logs.
            Throwable root = ex;
            while (root.getCause() != null) root = root.getCause();
            log.error("Password reset delivery failed: {} / {}",
                    ex.getClass().getSimpleName(), root.getClass().getSimpleName());
        }

        model.addAttribute(
                "success",
                "If the email belongs to an account, "
                        + "we'll send a password reset link."
        );

        return "auth/forgot-password";
    }

    @GetMapping("/reset-password")
    public String resetPage(
            @RequestParam(required = false) String token,
            Model model) {

        if (!resetService.isTokenValid(token)) {
            model.addAttribute("invalid", true);
            return "auth/reset-password";
        }

        model.addAttribute("token", token);

        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(
            @RequestParam String token,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model) {

        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match");
            model.addAttribute("token", token);
            return "auth/reset-password";
        }

        try {
            resetService.resetPassword(token, password);

            return "redirect:/login?reset";

        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("token", token);
            model.addAttribute(
                    "invalid",
                    !resetService.isTokenValid(token)
            );

            return "auth/reset-password";
        }
    }
}