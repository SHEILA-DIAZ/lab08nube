package com.techstore.inventory.controller;
import com.techstore.inventory.model.*;
import com.techstore.inventory.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@RestController @RequestMapping("/api/users")
public class UserController {
    private final UserRepository users;
    public UserController(UserRepository users) { this.users=users; }
    @GetMapping @PreAuthorize("hasAnyRole('ADMIN','AUDITOR')")
    public List<Map<String,Object>> list() { return users.findAll().stream().map(MeController::view).toList(); }
    public record UserUpdate(@NotNull Role role, @NotBlank @Size(max=100) String store,
                             @NotBlank @Size(max=150) String fullName) {}
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> update(@PathVariable Long id, @Valid @RequestBody UserUpdate r) {
        User u = get(id); u.setRole(r.role()); u.setStore(r.store().trim()); u.setFullName(r.fullName().trim());
        u.setTokenVersion(u.getTokenVersion()+1); return MeController.view(users.save(u));
    }
    @PostMapping("/{id}/unlock") @PreAuthorize("hasRole('ADMIN')")
    public Map<String,Object> unlock(@PathVariable Long id) {
        User u=get(id); u.setAccountLocked(false); u.setFailedAttempts(0); u.setMfaFailedAttempts(0);
        u.setLockTime(null); u.setTokenVersion(u.getTokenVersion()+1); return MeController.view(users.save(u));
    }
    private User get(Long id) { return users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Usuario no encontrado")); }
}