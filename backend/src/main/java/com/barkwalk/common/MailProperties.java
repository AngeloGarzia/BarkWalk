package com.barkwalk.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "barkwalk.mail")
public class MailProperties {
    private String from = "noreply@barkwalk.local";
    /** Clé API Brevo (optionnel si SMTP user/password déjà dans spring.mail). */
    private String brevoApiKey = "";
    private boolean enabled = true;

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getBrevoApiKey() {
        return brevoApiKey;
    }

    public void setBrevoApiKey(String brevoApiKey) {
        this.brevoApiKey = brevoApiKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
