package com.techstore.inventory.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    @Column(nullable = false)
    private String fullName;

    private String store;

    @Enumerated(EnumType.STRING)
    private Role role = Role.EMPLEADO;

    // Bloqueo por intentos fallidos
    private int failedAttempts = 0;
    private boolean accountLocked = false;
    private LocalDateTime lockTime;

    // MFA
    private String mfaSecret;
    private boolean mfaEnabled = false;
    private int mfaFailedAttempts = 0;
    private long tokenVersion = 0;
    private String providerSubject;

    // LOCAL, GOOGLE, GITHUB
    private String provider = "LOCAL";
}
