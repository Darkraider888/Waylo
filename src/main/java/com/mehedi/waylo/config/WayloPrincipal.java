package com.mehedi.waylo.config;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

public class WayloPrincipal extends org.springframework.security.core.userdetails.User {
    private static final long serialVersionUID = 1L;
    private final int authVersion;
    public WayloPrincipal(com.mehedi.waylo.entity.User user) {
        super(user.getEmail(), user.getPassword(), user.isEnabled(), true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        this.authVersion = user.getAuthVersion();
    }
    public int getAuthVersion() { return authVersion; }
}
