package com.mehedi.waylo.controller;

import com.mehedi.waylo.dto.DestinationRequest;
import com.mehedi.waylo.dto.TripRequest;
import com.mehedi.waylo.service.OrganizerTripService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/organizer/trips")
public class OrganizerTripController {

    private final OrganizerTripService service;

    @Value("${waylo.google-maps-embed-key:}")
    private String mapsKey;

    public OrganizerTripController(
            OrganizerTripService service) {

        this.service = service;
    }

    @GetMapping
    public String dashboard(
            Authentication authentication,
            Model model) {

        model.addAttribute(
                "trips",
                service.myTrips(authentication.getName())
        );

        return "organizer/trips";
    }

    private String form(Model model, Long tripId) {

        model.addAttribute("tripId", tripId);

        model.addAttribute(
                "destinations",
                service.destinations()
        );

        model.addAttribute("mapsEmbedKey", mapsKey);

        if (!model.containsAttribute("destinationRequest")) {
            model.addAttribute(
                    "destinationRequest",
                    new DestinationRequest()
            );
        }

        if (model.containsAttribute("createdDestinationId")) {

            Long destinationId = (Long) model.getAttribute(
                    "createdDestinationId"
            );

            TripRequest request = (TripRequest) model.getAttribute(
                    "tripRequest"
            );

            if (request != null && destinationId != null) {
                request.setDestinationId(destinationId);
            }
        }

        return "organizer/trip-form";
    }

    @GetMapping("/new")
    public String createForm(Authentication authentication, Model model) {

        service.requireGlobalManager(authentication.getName());
        model.addAttribute(
                "tripRequest",
                new TripRequest()
        );

        return form(model, null);
    }

    @GetMapping("/{id}/edit")
    public String edit(
            @PathVariable Long id,
            Authentication authentication,
            Model model) {

        model.addAttribute(
                "tripRequest",
                service.editForm(authentication.getName(), id)
        );
        model.addAttribute("coverImageUrl", service.coverForEdit(authentication.getName(), id));

        return form(model, id);
    }

    @PostMapping
    public String create(
            @Valid @ModelAttribute("tripRequest") TripRequest request,
            BindingResult errors,
            @RequestParam(name = "coverPhoto", required = false) MultipartFile coverPhoto,
            Authentication authentication,
            Model model,
            RedirectAttributes flash) {

        if (errors.hasErrors()) {
            return form(model, null);
        }

        try {
            service.create(authentication.getName(), request, coverPhoto);

        } catch (IllegalArgumentException ex) {

            model.addAttribute("error", ex.getMessage());
            return form(model, null);
        }

        flash.addFlashAttribute(
                "success",
                "Draft saved. Review it before publishing."
        );

        return "redirect:/organizer/trips";
    }

    @PostMapping("/{id}/edit")
    public String update(
            @PathVariable Long id,
            @Valid @ModelAttribute("tripRequest") TripRequest request,
            BindingResult errors,
            @RequestParam(name = "coverPhoto", required = false) MultipartFile coverPhoto,
            Authentication authentication,
            Model model,
            RedirectAttributes flash) {

        if (errors.hasErrors()) {
            return form(model, id);
        }

        try {
            service.update(authentication.getName(), id, request, coverPhoto);

        } catch (IllegalArgumentException ex) {

            model.addAttribute("error", ex.getMessage());
            return form(model, id);
        }

        flash.addFlashAttribute(
                "success",
                "Draft updated."
        );

        return "redirect:/organizer/trips";
    }

    @PostMapping("/{id}/cover")
    public String replaceCover(
            @PathVariable Long id,
            @RequestParam("coverPhoto") MultipartFile coverPhoto,
            Authentication authentication,
            RedirectAttributes flash) {

        try {
            service.replaceCover(authentication.getName(), id, coverPhoto);
            flash.addFlashAttribute("success", "Trip cover updated successfully.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/organizer/trips";
    }

    @PostMapping("/{id}/publish")
    public String publish(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes flash) {

        service.publish(authentication.getName(), id);

        flash.addFlashAttribute(
                "success",
                "Your trip is now visible on Explore Trips."
        );

        return "redirect:/organizer/trips";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String invalid(
            IllegalArgumentException ex,
            RedirectAttributes flash) {

        flash.addFlashAttribute("error", ex.getMessage());

        return "redirect:/organizer/trips";
    }
}