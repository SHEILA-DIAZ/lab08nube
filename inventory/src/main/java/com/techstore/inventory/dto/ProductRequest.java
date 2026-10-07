package com.techstore.inventory.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record ProductRequest(@NotBlank @Size(max=150) String name,
    @NotNull @DecimalMin("0.01") @Digits(integer=12, fraction=2) BigDecimal price,
    @NotNull @Min(0) Integer stock, @NotBlank @Size(max=100) String store) {}