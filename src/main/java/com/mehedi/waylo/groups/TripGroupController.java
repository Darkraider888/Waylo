package com.mehedi.waylo.groups;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/groups/trips/{tripId}")
public class TripGroupController {
    private final TripGroupService service;

    public TripGroupController(TripGroupService service) { this.service = service; }

    @GetMapping
    public String page(@PathVariable Long tripId, @RequestParam(defaultValue = "0") long beforeId, Authentication auth, Model model) {
        TripGroupService.GroupPage group = service.page(auth.getName(), tripId, beforeId);
        model.addAttribute("beforeId", beforeId);
        model.addAttribute("oldestId", group.messages().isEmpty() ? 0 : group.messages().getFirst().id());
        model.addAttribute("trip", group.trip());
        model.addAttribute("moderator", group.moderator());
        model.addAttribute("viewerId", group.currentUserId());
        model.addAttribute("messages", group.messages());
        model.addAttribute("members", group.members());
        model.addAttribute("eligibleBookings", group.eligibleBookings());
        long last = group.messages().isEmpty() ? 0 : group.messages().get(group.messages().size() - 1).id();
        model.addAttribute("lastMessageId", last);
        return "groups/trip-chat";
    }

    @GetMapping("/messages")
    @ResponseBody
    public ResponseEntity<List<TripGroupService.MessageView>> updates(
            @PathVariable Long tripId, @RequestParam(defaultValue = "0") long beforeId,
            Authentication auth) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.updates(auth.getName(), tripId, beforeId));
    }

    @PostMapping("/messages")
    public String post(@PathVariable Long tripId, @RequestParam String content,
                       Authentication auth, RedirectAttributes flash) {
        try { service.send(auth.getName(), tripId, content); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/groups/trips/" + tripId;
    }

    @PostMapping("/messages/{messageId}/remove")
    public String removeMessage(@PathVariable Long tripId, @PathVariable Long messageId,
                                Authentication auth) {
        service.removeMessage(auth.getName(), tripId, messageId);
        return "redirect:/groups/trips/" + tripId;
    }

    @PostMapping("/messages/{messageId}/edit")
    public String editMessage(@PathVariable Long tripId, @PathVariable Long messageId, @RequestParam String content,
            Authentication auth, RedirectAttributes flash) {
        try { service.editMessage(auth.getName(),tripId,messageId,content); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error",ex.getMessage()); }
        return "redirect:/groups/trips/" + tripId;
    }

    @PostMapping("/members/{userId}/remove")
    public String removeMember(@PathVariable Long tripId, @PathVariable Long userId,
                               @RequestParam String reason, Authentication auth,
                               RedirectAttributes flash) {
        try { service.setMembership(auth.getName(), tripId, userId, false, reason); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/groups/trips/" + tripId;
    }

    @PostMapping("/members/{userId}/add")
    public String addMember(@PathVariable Long tripId, @PathVariable Long userId,
                            @RequestParam String reason, Authentication auth, RedirectAttributes flash) {
        try { service.setMembership(auth.getName(), tripId, userId, true, reason); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/groups/trips/" + tripId;
    }
}
