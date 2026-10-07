package com.techstore.inventory.service;

import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import org.springframework.stereotype.Service;

@Service
public class MfaService {

    private final SecretGenerator secretGenerator =
            new DefaultSecretGenerator();

    private final CodeGenerator codeGenerator =
            new DefaultCodeGenerator();

    private final TimeProvider timeProvider =
            new SystemTimeProvider();

    private final DefaultCodeVerifier codeVerifier =
            new DefaultCodeVerifier(codeGenerator, timeProvider);

    public MfaService() {
        codeVerifier.setTimePeriod(30);
        codeVerifier.setAllowedTimePeriodDiscrepancy(1);
    }

    // Generar clave secreta
    public String generateSecret() {
        return secretGenerator.generate();
    }

    // Verificar código TOTP de 6 dígitos
    public boolean verifyCode(String secret, String code) {
        return secret != null && code != null && code.matches("[0-9]{6}") && codeVerifier.isValidCode(secret, code);
    }

    // Generar URI para Google Authenticator
    public String generateOtpAuthUrl(String email, String secret) {
        return "otpauth://totp/TechStore:"
                + java.net.URLEncoder.encode(email, java.nio.charset.StandardCharsets.UTF_8)
                + "?secret="
                + secret
                + "&issuer=TechStore&algorithm=SHA1&digits=6&period=30";
    }
}
