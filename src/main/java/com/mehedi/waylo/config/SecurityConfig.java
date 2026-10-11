package com.mehedi.waylo.config;

import com.mehedi.waylo.entity.User;
import com.mehedi.waylo.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(
            UserRepository userRepository) {

        return email -> {

            User user = userRepository.findByEmail(email.trim().toLowerCase(java.util.Locale.ROOT))
                    .orElseThrow(() ->
                            new UsernameNotFoundException(
                                    "Invalid email or password"
                            )
                    );

            if (!user.isEnabled()) {
                throw new DisabledException(
                        "This account is disabled"
                );
            }

            return new WayloPrincipal(user);
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/",
                                "/login",
                                "/register",
                                "/forgot-password",
                                "/reset-password",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/media/**",
                                "/favicon.ico",
                                "/icons/**", "/healthz", "/error"
                        ).permitAll()

                        // Specific private GET pages must precede wildcard public URLs.
                        .requestMatchers("/posts/new", "/profile/edit", "/saved", "/bookings/**")
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.GET,
                                "/trips",
                                "/trips/*",
                                "/feed",
                                "/search",
                                "/profile/*",
                                "/posts/*",
                                "/api/v1/destinations"
                        ).permitAll()

                        .requestMatchers("/payments/sslcommerz/success", "/payments/sslcommerz/fail",
                                "/payments/sslcommerz/cancel", "/payments/sslcommerz/ipn").permitAll()

                        .requestMatchers("/trip-proposals/**", "/groups/**")
                        .authenticated()

                        .requestMatchers("/admin/**")
                        .hasRole("ADMIN")

                        .requestMatchers("/organizer/destinations/**")
                        .hasAnyRole("ORGANIZER", "ADMIN")

                        // Scoped trip managers are MEMBERs; service checks ownership
                        // AND an approved proposal for exactly that trip.
                        .requestMatchers("/organizer/trips/**", "/organizer/trips")
                        .authenticated()

                        .requestMatchers("/organizer/**")
                        .hasAnyRole("ORGANIZER", "ADMIN")

                        .anyRequest().authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/feed", false)
                        .failureUrl("/login?error")
                        .permitAll()
                )

                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )

                .headers(headers -> headers
                        .referrerPolicy(policy -> policy.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .permissionsPolicy(policy -> policy.policy("camera=(), microphone=(), geolocation=()")))
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/payments/sslcommerz/success",
                        "/payments/sslcommerz/fail",
                        "/payments/sslcommerz/cancel",
                        "/payments/sslcommerz/ipn"
                ));

        return http.build();
    }
}
