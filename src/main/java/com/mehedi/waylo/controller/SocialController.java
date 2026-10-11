package com.mehedi.waylo.controller;

import com.mehedi.waylo.dto.CreatePostRequest;
import com.mehedi.waylo.dto.UpdateProfileRequest;
import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.service.SocialService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SocialController {

    private final SocialService service;

    public SocialController(SocialService service) {
        this.service = service;
    }

    private String viewer(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) return null;
        return authentication.getName();
    }

    @GetMapping("/feed")
    public String feed(@RequestParam(defaultValue = "0") int page,
                       Authentication authentication, Model model) {
        model.addAttribute("posts", service.feed(page, viewer(authentication)));
        return "social/feed";
    }

    @GetMapping("/posts/new")
    public String newPost(Model model) {
        if (!model.containsAttribute("postRequest")) {
            model.addAttribute("postRequest", new CreatePostRequest());
        }
        populatePostForm(model);
        return "social/create-post";
    }

    @PostMapping("/posts")
    public String createPost(
            @Valid @ModelAttribute("postRequest") CreatePostRequest request,
            BindingResult errors,
            @RequestParam(name = "photo", required = false) MultipartFile photo,
            Authentication authentication, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            populatePostForm(model);
            return "social/create-post";
        }
        try {
            service.createPost(authentication.getName(), request, photo);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            populatePostForm(model);
            return "social/create-post";
        }
        flash.addFlashAttribute("success", "Your travel story has been shared.");
        return "redirect:/feed";
    }

    private void populatePostForm(Model model) {
        model.addAttribute("destinations", service.destinations());
        model.addAttribute("trips", service.publishedTrips());
    }

    @GetMapping("/posts/{id}")
    public String postDetails(@PathVariable Long id,
                              @RequestParam(defaultValue = "0") int page,
                              Authentication authentication, Model model) {
        model.addAttribute("card", service.post(id, viewer(authentication)));
        model.addAttribute("comments", service.comments(id, page));
        return "social/post-details";
    }

    @PostMapping("/posts/{id}/like")
    public String like(@PathVariable Long id,
                       @RequestParam(defaultValue = "feed") String back,
                       Authentication authentication) {
        service.toggleLike(id, authentication.getName());
        return back.equals("post") ? "redirect:/posts/" + id : "redirect:/feed";
    }

    @PostMapping("/posts/{id}/save")
    public String save(@PathVariable Long id,
                       @RequestParam(defaultValue = "feed") String back,
                       Authentication authentication) {
        service.toggleSave(id, authentication.getName());
        return back.equals("post") ? "redirect:/posts/" + id : "redirect:/feed";
    }

    @PostMapping("/posts/{id}/comments")
    public String comment(@PathVariable Long id,
                          @RequestParam String body,
                          Authentication authentication,
                          RedirectAttributes flash) {
        try {
            service.comment(id, authentication.getName(), body);
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/posts/" + id + "#comments";
    }

    @PostMapping("/posts/{id}/delete")
    public String delete(@PathVariable Long id, Authentication authentication,
                         RedirectAttributes flash) {
        service.deletePost(id, authentication.getName());
        flash.addFlashAttribute("success", "Post deleted.");
        return "redirect:/feed";
    }

    @GetMapping("/saved")
    public String saved(@RequestParam(defaultValue = "0") int page,
                        Authentication authentication, Model model) {
        model.addAttribute("posts", service.savedPosts(authentication.getName(), page));
        return "social/saved";
    }

    @GetMapping("/profile")
    public String myProfile(Authentication authentication) {
        User user = service.myProfile(authentication.getName());
        return "redirect:/profile/" + user.getId();
    }

    @GetMapping("/profile/{id}")
    public String profile(@PathVariable Long id,
                          @RequestParam(defaultValue = "0") int page,
                          Authentication authentication, Model model) {
        User profileUser = service.profile(id);
        String email = viewer(authentication);
        boolean owner = email != null && service.myProfile(email).getId().equals(id);
        model.addAttribute("profileUser", profileUser);
        model.addAttribute("posts", service.userPosts(id, page, email));
        model.addAttribute("postCount", service.postCount(id));
        model.addAttribute("followers", service.followerCount(id));
        model.addAttribute("following", service.followingCount(id));
        model.addAttribute("isFollowing", service.following(email, id));
        model.addAttribute("owner", owner);
        return "social/profile";
    }

    @PostMapping("/profile/{id}/follow")
    public String follow(@PathVariable Long id, Authentication authentication) {
        service.toggleFollow(id, authentication.getName());
        return "redirect:/profile/" + id;
    }

    @GetMapping("/profile/edit")
    public String editProfile(Authentication authentication, Model model) {
        User user = service.myProfile(authentication.getName());
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setFullName(user.getFullName());
        request.setBio(user.getBio());
        request.setProfileLocation(user.getProfileLocation());
        model.addAttribute("profileRequest", request);
        model.addAttribute("existingAvatarUrl", user.getAvatarUrl());
        return "social/edit-profile";
    }

    @PostMapping("/profile/edit")
    public String updateProfile(
            @Valid @ModelAttribute("profileRequest") UpdateProfileRequest request,
            BindingResult errors,
            @RequestParam(name = "avatar", required = false) MultipartFile avatar,
            Authentication authentication, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) return "social/edit-profile";
        try {
            service.updateProfile(authentication.getName(), request, avatar);
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return "social/edit-profile";
        }
        flash.addFlashAttribute("success", "Profile updated.");
        return "redirect:/profile";
    }

    @GetMapping("/posts/{id}/edit")
    public String editPost(@PathVariable Long id, Authentication auth, Model model) {
        model.addAttribute("post", service.ownPost(id, auth.getName()));
        return "social/edit-post";
    }

    @PostMapping("/posts/{id}/edit")
    public String editPost(@PathVariable Long id, @RequestParam String caption,
            @RequestParam(required = false) MultipartFile photo, Authentication auth, RedirectAttributes flash) {
        try { service.editPost(id, auth.getName(), caption, photo); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); return "redirect:/posts/" + id + "/edit"; }
        flash.addFlashAttribute("success", "Story updated.");
        return "redirect:/posts/" + id;
    }

    @PostMapping("/posts/{postId}/comments/{id}/edit")
    public String editComment(@PathVariable Long postId, @PathVariable Long id, @RequestParam String body,
            Authentication auth, RedirectAttributes flash) {
        try { service.changeComment(postId, id, auth.getName(), body, false); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/posts/" + postId + "#comments";
    }

    @PostMapping("/posts/{postId}/comments/{id}/delete")
    public String deleteComment(@PathVariable Long postId, @PathVariable Long id, Authentication auth) {
        service.changeComment(postId, id, auth.getName(), null, true);
        return "redirect:/posts/" + postId + "#comments";
    }
}
