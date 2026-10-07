package com.techstore.inventory.model;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
@Entity @Getter @Setter
public class AuthChallenge {
    @Id private String id;
    @ManyToOne(optional = false) private User user;
    private String purpose;
    private Instant expiresAt;
    private long tokenVersion;
}