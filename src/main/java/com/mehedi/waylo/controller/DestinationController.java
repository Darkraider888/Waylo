package com.mehedi.waylo.controller;

import com.mehedi.waylo.dto.DestinationRequest;
import com.mehedi.waylo.entity.Destination;
import com.mehedi.waylo.service.DestinationService;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
public class DestinationController {

    private final DestinationService service;

    public DestinationController(DestinationService service) {
        this.service = service;
    }

    public record DestinationOption(
            Long id,
            String name,
            String district,
            String country,
            BigDecimal latitude,
            BigDecimal longitude
    ) {}

    @GetMapping("/organizer/destinations/search")
    @ResponseBody
    public List<DestinationOption> search(
            @RequestParam(defaultValue = "") String q,
            Authentication authentication) {

        return service.search(authentication.getName(), q)
                .stream()
                .map(destination -> new DestinationOption(
                        destination.getId(),
                        destination.getName(),
                        destination.getDistrict(),
                        destination.getCountry(),
                        destination.getLatitude(),
                        destination.getLongitude()
                ))
                .toList();
    }

    @PostMapping("/organizer/destinations")
    public String create(
            @Valid @ModelAttribute
            DestinationRequest destinationRequest,
            BindingResult errors,
            @RequestParam(required = false) Long tripId,
            Authentication authentication,
            RedirectAttributes flash) {

        String returnTo = tripId == null
                ? "/organizer/trips/new"
                : "/organizer/trips/" + tripId + "/edit";

        if (errors.hasErrors()) {

            flash.addFlashAttribute(
                    "destinationError",
                    "Check the destination fields and coordinates."
            );

            return "redirect:" + returnTo;
        }

        try {

            Destination destination = service.add(
                    authentication.getName(),
                    destinationRequest
            );

            flash.addFlashAttribute(
                    "createdDestinationId",
                    destination.getId()
            );

            flash.addFlashAttribute(
                    "success",
                    "Destination added and selected."
            );

        } catch (IllegalArgumentException ex) {

            flash.addFlashAttribute(
                    "destinationError",
                    ex.getMessage()
            );
        }

        return "redirect:" + returnTo;
    }

    // AJAX endpoint used inside the trip editor. Keeps unsaved trip fields intact.
    @PostMapping(value = "/organizer/destinations/inline", consumes = "application/json")
    @ResponseBody
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public DestinationOption createInline(
            @Valid @RequestBody DestinationRequest request,
            Authentication authentication) {
        Destination d = service.add(authentication.getName(), request);
        return new DestinationOption(d.getId(), d.getName(), d.getDistrict(),
                d.getCountry(), d.getLatitude(), d.getLongitude());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    @ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
    public java.util.Map<String, String> invalidDestination(IllegalArgumentException error) {
        return java.util.Map.of("error", error.getMessage());
    }

    @GetMapping("/admin/destinations")
    public String adminDestinations(
            Authentication authentication,
            Model model) {

        model.addAttribute(
                "destinations",
                service.all(authentication.getName())
        );

        return "admin/destinations";
    }

    @PostMapping("/admin/destinations/{id}/edit")
    public String update(@PathVariable Long id, @Valid @ModelAttribute DestinationRequest request, BindingResult errors,
            @RequestParam(required=false) org.springframework.web.multipart.MultipartFile photo,
            Authentication auth, RedirectAttributes flash) {
        if(errors.hasErrors()) { flash.addFlashAttribute("error","Check destination fields and coordinates."); return "redirect:/admin/destinations"; }
        try { service.update(auth.getName(),id,request,photo);flash.addFlashAttribute("success","Destination updated."); }
        catch(IllegalArgumentException ex) { flash.addFlashAttribute("error",ex.getMessage()); }
        return "redirect:/admin/destinations";
    }

    @PostMapping("/admin/destinations/{id}/delete")
    public String delete(
            @PathVariable Long id,
            Authentication authentication,
            RedirectAttributes flash) {

        try {

            service.delete(authentication.getName(), id);

            flash.addFlashAttribute(
                    "success",
                    "Destination deleted successfully."
            );

        } catch (IllegalArgumentException ex) {

            flash.addFlashAttribute(
                    "error",
                    ex.getMessage()
            );
        }

        return "redirect:/admin/destinations";
    }
}