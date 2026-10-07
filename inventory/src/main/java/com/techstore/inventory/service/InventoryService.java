package com.techstore.inventory.service;
import com.techstore.inventory.dto.ProductRequest;
import com.techstore.inventory.model.*;
import com.techstore.inventory.repository.ProductRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.*;
@Service
public class InventoryService {
    private final ProductRepository products;
    public InventoryService(ProductRepository products) { this.products = products; }
    public List<Product> list(User user) {
        return user.getRole() == Role.ADMIN || user.getRole() == Role.AUDITOR
            ? products.findAll() : products.findByStoreOrderByIdAsc(user.getStore());
    }
    public Product get(Long id, User user) {
        Product p = products.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado"));
        scope(user, p.getStore()); return p;
    }
    private void scope(User user, String store) {
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.AUDITOR && !Objects.equals(user.getStore(), store))
            throw new AccessDeniedException("Operacion fuera de tu tienda");
    }
    private void manage(User user) {
        if (user.getRole() != Role.ADMIN && user.getRole() != Role.GERENTE)
            throw new AccessDeniedException("Solo administrador o gerente");
    }
    @Transactional
    public Product create(ProductRequest req, User user) {
        manage(user); scope(user, req.store().trim());
        Product p = new Product(); apply(p, req); return products.save(p);
    }
    @Transactional
    public Product update(Long id, ProductRequest req, User user) {
        manage(user); Product p = get(id, user); scope(user, req.store().trim());
        apply(p, req); return products.save(p);
    }
    private void apply(Product p, ProductRequest r) {
        p.setName(r.name().trim()); p.setPrice(r.price()); p.setStock(r.stock()); p.setStore(r.store().trim());
    }
    @Transactional
    public Product stock(Long id, int stock, User user) {
        if (user.getRole() == Role.AUDITOR) throw new AccessDeniedException("Auditor solo tiene lectura");
        Product p = get(id, user); p.setStock(stock); return products.save(p);
    }
    @Transactional
    public void delete(Long id, User user) { manage(user); products.delete(get(id, user)); }
    public Map<String, Object> report(User user) {
        var list = list(user);
        long units = list.stream().mapToLong(Product::getStock).sum();
        BigDecimal value = list.stream().map(p -> p.getPrice().multiply(BigDecimal.valueOf(p.getStock())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Map.of("scope", user.getRole() == Role.GERENTE ? user.getStore() : "Todas las tiendas",
            "products", list, "productCount", list.size(), "totalStock", units, "inventoryValue", value);
    }
}