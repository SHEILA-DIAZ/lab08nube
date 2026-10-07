package com.techstore.inventory.controller;
import com.techstore.inventory.dto.ProductRequest;
import com.techstore.inventory.model.*;
import com.techstore.inventory.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class ProductController {
    private final InventoryService inventory;
    private final AuthService auth;
    public ProductController(InventoryService inventory, AuthService auth) { this.inventory=inventory; this.auth=auth; }
    private User user(Authentication a) { return auth.current(a.getName()); }
    @GetMapping("/products")
    public List<Product> list(Authentication a) { return inventory.list(user(a)); }
    @GetMapping("/products/{id}")
    public Product get(@PathVariable Long id, Authentication a) { return inventory.get(id, user(a)); }
    @PostMapping("/products") @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest r, Authentication a) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventory.create(r, user(a)));
    }
    @PutMapping("/products/{id}") @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public Product update(@PathVariable Long id, @Valid @RequestBody ProductRequest r, Authentication a) {
        return inventory.update(id,r,user(a));
    }
    public record StockRequest(@NotNull @Min(0) Integer stock) {}
    @PatchMapping("/products/{id}/stock") @PreAuthorize("hasAnyRole('ADMIN','GERENTE','EMPLEADO')")
    public Product stock(@PathVariable Long id, @Valid @RequestBody StockRequest r, Authentication a) {
        return inventory.stock(id,r.stock(),user(a));
    }
    @DeleteMapping("/products/{id}") @PreAuthorize("hasAnyRole('ADMIN','GERENTE')")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication a) {
        inventory.delete(id,user(a)); return ResponseEntity.noContent().build();
    }
    @GetMapping("/reports/inventory") @PreAuthorize("hasAnyRole('ADMIN','GERENTE','AUDITOR')")
    public Map<String,Object> report(Authentication a) { return inventory.report(user(a)); }
}