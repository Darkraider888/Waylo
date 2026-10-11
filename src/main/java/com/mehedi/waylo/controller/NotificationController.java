package com.mehedi.waylo.controller;
import com.mehedi.waylo.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller
public class NotificationController {
    private final NotificationService service;
    public NotificationController(NotificationService service){this.service=service;}
    @GetMapping("/notifications") public String page(Authentication auth,Model model){model.addAttribute("notifications",service.list(auth.getName()));return "notifications";}
    @PostMapping("/notifications/{id}/read") public String read(@PathVariable Long id,Authentication auth){service.read(auth.getName(),id);return "redirect:/notifications";}
}
