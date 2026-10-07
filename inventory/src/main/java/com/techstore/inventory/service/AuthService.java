package com.techstore.inventory.service;
import com.techstore.inventory.dto.*;
import com.techstore.inventory.model.*;
import com.techstore.inventory.repository.*;
import com.techstore.inventory.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

@Service
public class AuthService {
    private static final int MAX_PASSWORD_ATTEMPTS = 5;
    private static final int MAX_MFA_ATTEMPTS = 3;
    private static final int CHALLENGE_SECONDS = 300;
    private final UserRepository users;
    private final AuthChallengeRepository challenges;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final MfaService mfa;
    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt,
                       MfaService mfa, AuthChallengeRepository challenges) {
        this.users = users; this.encoder = encoder; this.jwt = jwt;
        this.mfa = mfa; this.challenges = challenges;
    }
    public static String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    public synchronized User register(RegisterRequest req) {
        String email = normalizeEmail(req.getEmail());
        if (users.existsByEmail(email)) throw new IllegalArgumentException("El email ya esta registrado");
        User user = new User();
        user.setEmail(email); user.setPassword(encoder.encode(req.getPassword()));
        user.setFullName(req.getFullName().trim()); user.setStore(req.getStore().trim());
        user.setRole(Role.EMPLEADO);
        return users.save(user);
    }
    // Single-instance lab: serialize attempts; saves survive authentication exceptions.
    public synchronized Map<String, Object> login(LoginRequest req) {
        User user = users.findByEmail(normalizeEmail(req.getEmail()))
                .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas"));
        checkUnlocked(user);
        if (user.getPassword() == null || !encoder.matches(req.getPassword(), user.getPassword())) {
            user.setFailedAttempts(user.getFailedAttempts() + 1);
            if (user.getFailedAttempts() >= MAX_PASSWORD_ATTEMPTS) lock(user);
            users.save(user); checkUnlocked(user);
            throw new BadCredentialsException("Credenciales invalidas. Intentos restantes: " + (MAX_PASSWORD_ATTEMPTS-user.getFailedAttempts()));
        }
        user.setFailedAttempts(0); users.save(user);
        return completeFirstFactor(user);
    }
    public synchronized Map<String, Object> completeFirstFactor(User user) {
        checkUnlocked(user);
        if (user.isMfaEnabled()) return Map.of("mfaRequired", true,
                "challengeToken", newChallenge(user, "MFA"), "expiresIn", CHALLENGE_SECONDS);
        return tokenResponse(user);
    }
    public synchronized String newChallenge(User user, String purpose) {
        AuthChallenge c = new AuthChallenge();
        c.setId(UUID.randomUUID().toString()); c.setUser(user);
        c.setPurpose(purpose); c.setExpiresAt(Instant.now().plusSeconds(CHALLENGE_SECONDS));
        c.setTokenVersion(user.getTokenVersion()); challenges.save(c);
        return c.getId();
    }
    private AuthChallenge challenge(String id, String purpose) {
        if (id == null || id.isBlank()) throw new BadCredentialsException("Falta el desafio de autenticacion");
        AuthChallenge c = challenges.findById(id).orElseThrow(() -> new BadCredentialsException("Desafio invalido o consumido"));
        if (!purpose.equals(c.getPurpose()) || c.getExpiresAt().isBefore(Instant.now())
                || c.getTokenVersion() != c.getUser().getTokenVersion())
            throw new BadCredentialsException("Desafio invalido o expirado");
        checkUnlocked(c.getUser()); return c;
    }
    public synchronized Map<String, Object> exchangeOAuth(String code) {
        AuthChallenge c = challenge(code, "OAUTH");
        challenges.deleteById(c.getId()); return completeFirstFactor(c.getUser());
    }
    public synchronized Map<String, Object> verifyMfa(MfaVerifyRequest req) {
        AuthChallenge c = challenge(req.getChallengeToken(), "MFA");
        User user = c.getUser();
        if (!user.isMfaEnabled()) throw new BadCredentialsException("MFA no esta habilitado");
        verifyCode(user, req.getCode()); challenges.deleteById(c.getId());
        // Consume all other outstanding challenges after a successful second factor.
        user.setTokenVersion(user.getTokenVersion() + 1); users.save(user);
        return tokenResponse(user);
    }
    public User current(String email) {
        return users.findByEmail(email).orElseThrow(() -> new BadCredentialsException("Usuario no encontrado"));
    }
    public synchronized String setupMfa(String email) {
        User user = current(email); checkUnlocked(user);
        if (user.isMfaEnabled()) throw new IllegalArgumentException("MFA ya esta activo");
        user.setMfaSecret(mfa.generateSecret()); users.save(user);
        return mfa.generateOtpAuthUrl(user.getEmail(), user.getMfaSecret());
    }
    public synchronized Map<String, Object> enableMfa(String email, String code) {
        User user = current(email); checkUnlocked(user);
        if (user.isMfaEnabled() || user.getMfaSecret() == null)
            throw new IllegalArgumentException("Configura MFA antes de activarlo");
        verifyCode(user, code); user.setMfaEnabled(true);
        user.setTokenVersion(user.getTokenVersion()+1); users.save(user);
        return tokenResponse(user);
    }
    private void verifyCode(User user, String code) {
        if (!mfa.verifyCode(user.getMfaSecret(), code)) {
            user.setMfaFailedAttempts(user.getMfaFailedAttempts()+1);
            if (user.getMfaFailedAttempts() >= MAX_MFA_ATTEMPTS) lock(user);
            users.save(user); checkUnlocked(user);
            throw new BadCredentialsException("Codigo MFA incorrecto. Intentos restantes: " + (MAX_MFA_ATTEMPTS-user.getMfaFailedAttempts()));
        }
        user.setMfaFailedAttempts(0); users.save(user);
    }
    private void lock(User user) {
        user.setAccountLocked(true); user.setLockTime(LocalDateTime.now());
        user.setTokenVersion(user.getTokenVersion()+1);
    }
    private void checkUnlocked(User user) {
        if (user.isAccountLocked()) throw new LockedException("Cuenta bloqueada por intentos fallidos; solicita desbloqueo al administrador");
    }
    private Map<String, Object> tokenResponse(User user) {
        return Map.of("token", jwt.generateToken(user), "type", "Bearer", "mfaRequired", false);
    }
}
