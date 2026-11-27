package com.barkwalk.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Refuse le démarrage si JWT / signature média sont absents ou trop courts hors profil {@code dev}.
 */
@Component
public class SecretStartupValidator implements ApplicationRunner {
    private static final int MIN_SECRET_LENGTH = 32;

    private final Environment environment;
    private final String jwtSecret;
    private final String storageSigningSecret;

    public SecretStartupValidator(
            Environment environment,
            @Value("${barkwalk.jwt.secret:}") String jwtSecret,
            @Value("${barkwalk.storage.signing-secret:}") String storageSigningSecret) {
        this.environment = environment;
        this.jwtSecret = jwtSecret;
        this.storageSigningSecret = storageSigningSecret;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean dev = false;
        for (String profile : environment.getActiveProfiles()) {
            if ("dev".equalsIgnoreCase(profile) || "local".equalsIgnoreCase(profile) || "test".equalsIgnoreCase(profile)) {
                dev = true;
                break;
            }
        }
        // Sans profil explicite (souvent local) : tolère les secrets de application-dev via spring.profiles.default.
        if (!dev && environment.getActiveProfiles().length == 0) {
            String def = environment.getProperty("spring.profiles.default", "");
            dev = def.contains("dev") || def.contains("local");
        }
        validate("barkwalk.jwt.secret / JWT_SECRET", jwtSecret, dev);
        validate("barkwalk.storage.signing-secret / STORAGE_SIGNING_SECRET", storageSigningSecret, dev);
    }

    private static void validate(String name, String value, boolean allowWeakDev) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is required (min " + MIN_SECRET_LENGTH + " chars)");
        }
        if (value.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(name + " must be at least " + MIN_SECRET_LENGTH + " characters");
        }
        if (!allowWeakDev && (value.contains("change-me") || value.contains("barkwalk-dev"))) {
            throw new IllegalStateException(name + " still uses a development default — set a strong secret");
        }
    }
}
