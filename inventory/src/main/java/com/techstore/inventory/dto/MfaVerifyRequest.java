package com.techstore.inventory.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
@Getter @Setter
public class MfaVerifyRequest {
    private String email;
    private String challengeToken;
    @NotBlank(message = "El codigo es obligatorio")
    private String code;
}