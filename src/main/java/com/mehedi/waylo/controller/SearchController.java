package com.mehedi.waylo.controller;

import com.mehedi.waylo.repository.DestinationRepository;
import com.mehedi.waylo.repository.UserRepository;
import com.mehedi.waylo.service.TripService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class SearchController {
    private final TripService trips;
    private final DestinationRepository destinations;
    private final UserRepository users;

    public SearchController(TripService trips, DestinationRepository destinations,
                            UserRepository users) {
        this.trips = trips;
        this.destinations = destinations;
        this.users = users;
    }

    @GetMapping("/search")
    public String search(@RequestParam(defaultValue = "") String q, Model model) {
        String term = q.strip();
        if (term.length() > 80) term = term.substring(0, 80);
        model.addAttribute("query", term);
        model.addAttribute("trips", term.isBlank() ? java.util.List.of()
                : trips.searchPublishedTrips(term, null).stream().limit(12).toList());
        model.addAttribute("people", term.isBlank() ? java.util.List.of()
                : users.searchPublicNames(term, PageRequest.of(0, 12)));
        model.addAttribute("destinations", term.isBlank() ? java.util.List.of()
                : destinations.search(term).stream().limit(12).toList());
        return "social/search";
    }
}
