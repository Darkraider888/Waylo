package com.mehedi.waylo.config;

import com.mehedi.waylo.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

/** Invalidate stale browser sessions after password, role or enabled-state changes. */
@Component
@Order(-90)
public class AccountStateFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public AccountStateFilter(UserRepository users) { this.users=users; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getPrincipal() instanceof WayloPrincipal principal) {
            var user=users.findByEmail(principal.getUsername()).orElse(null);
            boolean valid=user!=null && user.isEnabled() && user.getAuthVersion()==principal.getAuthVersion()
                    && auth.getAuthorities().stream().anyMatch(a->a.getAuthority().equals("ROLE_"+user.getRole()));
            if(!valid) {
                var session=request.getSession(false);if(session!=null)session.invalidate();
                SecurityContextHolder.clearContext();response.sendRedirect("/login?sessionChanged");return;
            }
        }
        chain.doFilter(request,response);
    }
}
