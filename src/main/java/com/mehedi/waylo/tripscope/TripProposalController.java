package com.mehedi.waylo.tripscope;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TripProposalController {
    private final TripProposalService proposals;

    public TripProposalController(TripProposalService proposals) {
        this.proposals = proposals;
    }

    @GetMapping("/trip-proposals")
    public String myProposals(Authentication auth, Model model) {
        model.addAttribute("proposals", proposals.mine(auth.getName()));
        return "proposals/my-proposals";
    }

    @GetMapping("/trip-proposals/apply")
    public String applyForm(Model model) {
        if (!model.containsAttribute("proposalRequest"))
            model.addAttribute("proposalRequest", new TripProposalRequest());
        model.addAttribute("destinations", proposals.destinations());
        return "proposals/apply";
    }

    @PostMapping("/trip-proposals/apply")
    public String submit(@Valid @ModelAttribute("proposalRequest") TripProposalRequest request,
                         BindingResult result, Authentication auth, Model model,
                         RedirectAttributes flash) {
        if (!result.hasErrors()) {
            try {
                proposals.submit(auth.getName(), request);
                flash.addFlashAttribute("success", "Trip proposal submitted. A platform admin must review it.");
                return "redirect:/trip-proposals";
            } catch (IllegalArgumentException ex) {
                model.addAttribute("error", ex.getMessage());
            }
        }
        model.addAttribute("destinations", proposals.destinations());
        return "proposals/apply";
    }

    @PostMapping("/admin/trip-proposals/{id}/approve")
    public String approve(@PathVariable Long id,
                          @RequestParam(defaultValue = "false") boolean reviewed,
                          Authentication auth, RedirectAttributes flash) {
        try {
            Long tripId = proposals.review(id, auth.getName(), true, null, reviewed);
            flash.addFlashAttribute("success", "Proposal approved. Draft trip #" + tripId + " created for this applicant only.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("reviewError", ex.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

    @PostMapping("/admin/trip-proposals/{id}/reject")
    public String reject(@PathVariable Long id,
                         @RequestParam String rejectionReason,
                         Authentication auth, RedirectAttributes flash) {
        try {
            proposals.review(id, auth.getName(), false, rejectionReason, false);
            flash.addFlashAttribute("success", "Trip proposal rejected.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("reviewError", ex.getMessage());
        }
        return "redirect:/admin/dashboard";
    }
}
