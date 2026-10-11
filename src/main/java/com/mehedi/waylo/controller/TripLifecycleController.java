package com.mehedi.waylo.controller;
import com.mehedi.waylo.service.TripLifecycleService;
import com.mehedi.waylo.repository.TripRepository;
import com.mehedi.waylo.enums.TripStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
public class TripLifecycleController {
    private final TripLifecycleService service;private final TripRepository trips;
    public TripLifecycleController(TripLifecycleService service,TripRepository trips){this.service=service;this.trips=trips;}
    @GetMapping("/admin/trips") public String admin(Model model){model.addAttribute("trips",trips.findByStatus(TripStatus.PUBLISHED));return "admin/trips";}
    @PostMapping("/organizer/trips/{id}/status") public String organizer(@PathVariable Long id,@RequestParam boolean cancel,@RequestParam String reason,Authentication auth,RedirectAttributes flash){return change(id,cancel,reason,auth,flash,"/organizer/trips");}
    @PostMapping("/admin/trips/{id}/status") public String admin(@PathVariable Long id,@RequestParam boolean cancel,@RequestParam String reason,Authentication auth,RedirectAttributes flash){return change(id,cancel,reason,auth,flash,"/admin/trips");}
    private String change(Long id,boolean cancel,String reason,Authentication auth,RedirectAttributes flash,String back){
        try{service.change(auth.getName(),id,cancel,reason);flash.addFlashAttribute("success","Trip status updated. No payment was refunded or deleted.");}
        catch(IllegalArgumentException ex){flash.addFlashAttribute("error",ex.getMessage());}
        return "redirect:"+back;
    }
}
