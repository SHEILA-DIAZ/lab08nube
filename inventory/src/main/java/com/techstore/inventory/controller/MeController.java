package com.techstore.inventory.controller;
import com.techstore.inventory.model.User;
import com.techstore.inventory.service.AuthService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api")
public class MeController {
    private final AuthService auth;
    public MeController(AuthService auth) { this.auth = auth; }
    @GetMapping("/me")
    public Map<String, Object> me(Authentication principal) {
        return view(auth.current(principal.getName()));
    }
    public static Map<String, Object> view(User u) {
        return Map.of("id", u.getId(), "email", u.getEmail(), "fullName", u.getFullName(),
            "store", u.getStore(), "role", u.getRole(), "mfaEnabled", u.isMfaEnabled(),
            "accountLocked", u.isAccountLocked(), "authorities", "[ROLE_" + u.getRole() + "]");
    }
}