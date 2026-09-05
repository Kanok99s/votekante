package com.votekante.controllers;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Root and per-role landing pages. Spring Security redirects signed-in users
 * to {@code /dashboard}, which fans out by role.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String root(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        return "redirect:/login";
    }

    @GetMapping(value = "/hello", produces = "text/plain")
    @ResponseBody
    public String hello() {
        // Plain-text health check proving the app booted (permitted for all).
        return "Hello from VoteKante! The server is running.";
    }

    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
        return isAdmin ? "redirect:/admin" : "redirect:/voter";
    }
}
