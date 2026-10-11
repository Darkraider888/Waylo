package com.mehedi.waylo.controller;

import com.mehedi.waylo.booking.BookingService;
import com.mehedi.waylo.booking.SandboxPaymentGateway;
import com.mehedi.waylo.entity.Trip;
import com.mehedi.waylo.groups.TripGroupService;
import com.mehedi.waylo.repository.DestinationRepository;
import com.mehedi.waylo.service.TripService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
public class TripController {
    private static final ZoneId DHAKA = ZoneId.of("Asia/Dhaka");

    private final TripService tripService;
    private final DestinationRepository destinations;
    private final BookingService bookings;
    private final SandboxPaymentGateway gateway;
    private final TripGroupService groups;

    public TripController(TripService tripService, DestinationRepository destinations,
                          BookingService bookings, SandboxPaymentGateway gateway,
                          TripGroupService groups) {
        this.tripService = tripService;
        this.destinations = destinations;
        this.bookings = bookings;
        this.gateway = gateway;
        this.groups = groups;
    }

    private boolean signedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    private void navigation(Authentication auth, Model model) {
        model.addAttribute("signedIn", signedIn(auth));
    }

    @GetMapping("/trips")
    public String explore(@RequestParam(defaultValue = "") String q,
                          @RequestParam(required = false) Long destinationId,
                          Authentication auth, Model model) {
        navigation(auth, model);
        String term = q.trim();
        if (term.length() > 80) term = term.substring(0, 80);
        var found = tripService.searchPublishedTrips(term, destinationId);
        model.addAttribute("trips", found);
        model.addAttribute("query", term);
        model.addAttribute("destinationId", destinationId);
        model.addAttribute("destinations", destinations.findAll(org.springframework.data.domain.Sort.by("name")));
        model.addAttribute("seatAvailability", found.stream().collect(Collectors.toMap(
                Trip::getId, bookings::availableSeats, (left, right) -> left)));
        return "trips";
    }

    private Trip bookingView(Long id, Authentication auth, Model model) {
        navigation(auth, model);
        Trip trip = tripService.getPublishedTrip(id);
        int available = bookings.availableSeats(trip);
        boolean departed = !trip.getDepartureDate().isAfter(LocalDateTime.now(DHAKA));
        model.addAttribute("trip", trip);
        model.addAttribute("availableSeats", available);
        model.addAttribute("maxBookableSeats", Math.min(6, available));
        model.addAttribute("seatOptions", IntStream.rangeClosed(1, Math.min(available, 6)).boxed().toList());
        model.addAttribute("departed", departed);
        model.addAttribute("checkoutReady", gateway.ready() && !departed && available > 0);
        return trip;
    }

    @GetMapping("/trips/{id}")
    public String detail(@PathVariable Long id, Authentication auth, Model model) {
        bookingView(id, auth, model);
        model.addAttribute("groupAccess", groups.hasAccess(signedIn(auth) ? auth.getName() : null, id));
        return "trip-details";
    }

    // Separate calculator/checkout form. Browser sessions and CSRF remain enabled.
    // /trips/{id}/reserve is private through SecurityConfig's anyRequest().authenticated().
    @GetMapping("/trips/{id}/reserve")
    public String reserve(@PathVariable Long id, Authentication auth, Model model) {
        if (!signedIn(auth)) return "redirect:/login";
        bookingView(id, auth, model);
        return "booking/reserve";
    }
}
