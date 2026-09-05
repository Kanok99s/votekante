package com.votekante.controllers;

import com.votekante.services.UserService;
import com.votekante.services.VoteException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Public pages: the login screen and the self-service voter registration.
 *
 * <p>Registration always creates a {@code VOTER}. Logging in is delegated to
 * Spring Security's form login ({@code POST /perform-login}).</p>
 */
@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @PostMapping("/perform-register")
    public String register(@RequestParam String username,
                           @RequestParam String password,
                           Model model) {
        try {
            userService.registerVoter(username, password);
            return "redirect:/login?registered";
        } catch (VoteException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("username", username == null ? "" : username.trim());
            return "register";
        }
    }
}
