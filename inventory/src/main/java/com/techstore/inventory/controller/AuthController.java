package com.techstore.inventory.controller;
import com.techstore.inventory.dto.*;
import com.techstore.inventory.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        var user = auth.register(req);
        return ResponseEntity.ok(Map.of("message", "Usuario registrado correctamente", "email", user.getEmail(), "role", user.getRole()));
    }
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest req) { return auth.login(req); }
    @PostMapping("/mfa/setup")
    public Map<String, Object> setup(Authentication principal) {
        String uri = auth.setupMfa(principal.getName());
        return Map.of("otpauthUrl", uri, "secret", auth.current(principal.getName()).getMfaSecret());
    }
    @PostMapping("/mfa/enable")
    public Map<String, Object> enable(Authentication principal, @Valid @RequestBody MfaVerifyRequest req) {
        return auth.enableMfa(principal.getName(), req.getCode());
    }
    @PostMapping("/mfa/verify")
    public Map<String, Object> verify(@Valid @RequestBody MfaVerifyRequest req) { return auth.verifyMfa(req); }
    @PostMapping("/oauth2/exchange")
    public Map<String, Object> exchange(@RequestBody Map<String, String> req) { return auth.exchangeOAuth(req.get("code")); }
}
