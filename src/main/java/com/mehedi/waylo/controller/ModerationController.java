package com.mehedi.waylo.controller;

import com.mehedi.waylo.service.ModerationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ModerationController {
    private final ModerationService service;
    public ModerationController(ModerationService service) { this.service=service; }
    @PostMapping("/reports")
    public String report(@RequestParam String type,@RequestParam Long id,@RequestParam String reason,
            Authentication auth,RedirectAttributes flash) {
        try { service.report(auth.getName(),type,id,reason); flash.addFlashAttribute("success","Report sent to the moderation queue."); }
        catch(IllegalArgumentException ex) { flash.addFlashAttribute("error",ex.getMessage()); }
        return "redirect:/feed";
    }
    @GetMapping("/admin/moderation")
    public String page(Authentication auth,Model model) {
        model.addAttribute("reports",service.reports(auth.getName()));
        model.addAttribute("audit",service.audit(auth.getName()));
        model.addAttribute("overview",service.overview(auth.getName()));
        return "admin/moderation";
    }
    @PostMapping("/admin/reports/{id}/review")
    public String review(@PathVariable Long id,@RequestParam boolean hide,@RequestParam String reason,
            Authentication auth,RedirectAttributes flash) {
        try { service.review(auth.getName(),id,hide,reason); flash.addFlashAttribute("success","Review recorded."); }
        catch(IllegalArgumentException ex) { flash.addFlashAttribute("error",ex.getMessage()); }
        return "redirect:/admin/moderation";
    }
}
