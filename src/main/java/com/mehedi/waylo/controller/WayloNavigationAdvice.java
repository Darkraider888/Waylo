package com.mehedi.waylo.controller;

import com.mehedi.waylo.tripscope.TripProposalService;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class WayloNavigationAdvice {
    private final TripProposalService proposals;

    public WayloNavigationAdvice(TripProposalService proposals) {
        this.proposals = proposals;
    }
    private boolean isSignedIn(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    @ModelAttribute("loggedIn")
    public boolean loggedIn(Authentication authentication) {
        return isSignedIn(authentication);
    }

    @ModelAttribute("viewerEmail")
    public String viewerEmail(Authentication authentication) {
        return isSignedIn(authentication) ? authentication.getName() : "";
    }

    @ModelAttribute("canManageTrips")
    public boolean canManageTrips(Authentication authentication) {
        return canCreateTrips(authentication)
                || (isSignedIn(authentication) && proposals.hasApprovedTrip(authentication.getName()));
    }

    @ModelAttribute("canCreateTrips")
    public boolean canCreateTrips(Authentication authentication) {
        return isSignedIn(authentication) && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ORGANIZER".equals(a.getAuthority())
                        || "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @ModelAttribute("isAdmin")
    public boolean admin(Authentication authentication) {
        return isSignedIn(authentication) && authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
