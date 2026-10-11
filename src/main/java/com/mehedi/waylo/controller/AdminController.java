package com.mehedi.waylo.controller;

import com.mehedi.waylo.booking.BookingRepository;
import com.mehedi.waylo.booking.PaymentAttemptRepository;
import com.mehedi.waylo.enums.TripStatus;
import com.mehedi.waylo.repository.TripRepository;
import com.mehedi.waylo.service.OrganizerApplicationService;
import com.mehedi.waylo.tripscope.TripProposalService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final OrganizerApplicationService service;
    private final TripProposalService tripProposals;
    private final TripRepository trips;
    private final BookingRepository bookings;
    private final PaymentAttemptRepository attempts;

    public AdminController(OrganizerApplicationService service,
                           TripProposalService tripProposals,
                           TripRepository trips,
                           BookingRepository bookings,
                           PaymentAttemptRepository attempts) {
        this.service = service;
        this.tripProposals = tripProposals;
        this.trips = trips;
        this.bookings = bookings;
        this.attempts = attempts;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String invalidReview(IllegalArgumentException exception, RedirectAttributes attributes) {
        attributes.addFlashAttribute("reviewError", exception.getMessage());
        return "redirect:/admin/dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        model.addAttribute("applications", service.pendingApplications());
        model.addAttribute("tripProposals", tripProposals.pending());
        model.addAttribute("publishedGroups", trips.findByStatus(TripStatus.PUBLISHED));

        return "admin/dashboard";
    }

    @GetMapping("/payment-reviews")
    public String paymentReviews(Model model) {
        model.addAttribute("bookingReviews", bookings.findByStatusOrderByCreatedAtDesc("REVIEW"));
        model.addAttribute("paymentReviews", attempts.findByStatusOrderByCreatedAtDesc("REVIEW"));
        return "admin/payment-reviews";
    }

    @PostMapping("/applications/{id}/approve")
    public String approve(
            @PathVariable Long id,
            Authentication authentication) {

        service.review(
                id,
                authentication.getName(),
                true,
                null
        );

        return "redirect:/admin/dashboard?approved";
    }

    @PostMapping("/applications/{id}/reject")
    public String reject(
            @PathVariable Long id,
            @RequestParam String rejectionReason,
            Authentication authentication) {

        service.review(
                id,
                authentication.getName(),
                false,
                rejectionReason
        );

        return "redirect:/admin/dashboard?rejected";
    }
}