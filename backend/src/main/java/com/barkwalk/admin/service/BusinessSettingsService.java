package com.barkwalk.admin.service;

import com.barkwalk.admin.BusinessSettingsKeys;
import com.barkwalk.admin.domain.Setting;
import com.barkwalk.admin.dto.AdminDtos.SettingResponse;
import com.barkwalk.admin.repository.SettingRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

// Lecture typée des paramètres métier + mise à jour admin (valeurs jamais en dur dans les règles).
@Service
public class BusinessSettingsService {
    private static final Map<String, DefaultSetting> DEFAULTS = new LinkedHashMap<>();

    static {
        DEFAULTS.put(BusinessSettingsKeys.CANCELLATION_FREE_HOURS,
                new DefaultSetting("24", "R4 Annulation gratuite (heures)"));
        DEFAULTS.put(BusinessSettingsKeys.PENDING_PAYMENT_MINUTES,
                new DefaultSetting("15", "R6 Libération place non payée (minutes)"));
        DEFAULTS.put(BusinessSettingsKeys.WAITLIST_OFFER_HOURS,
                new DefaultSetting("2", "R7 Délai offre liste d'attente (heures)"));
        DEFAULTS.put(BusinessSettingsKeys.COMMUNITY_WALKS_THRESHOLD,
                new DefaultSetting("3", "R8 Seuil balades terminées pour organisateur"));
        DEFAULTS.put(BusinessSettingsKeys.COMMUNITY_CAPACITY_MAX,
                new DefaultSetting("8", "R11 Capacité max balade communautaire"));
        DEFAULTS.put(BusinessSettingsKeys.INDEP_COMMISSION_PERCENT,
                new DefaultSetting("15", "R12 Commission indépendants (%) — valeur provisoire"));
        DEFAULTS.put(BusinessSettingsKeys.REVIEW_WINDOW_DAYS,
                new DefaultSetting("14", "R13 Fenêtre pour laisser un avis (jours)"));
        DEFAULTS.put(BusinessSettingsKeys.ORG_MIN_RATING,
                new DefaultSetting("3", "R10 Note moyenne mini organisateur (/5)"));
        DEFAULTS.put(BusinessSettingsKeys.ORG_MAX_REPORTS,
                new DefaultSetting("2", "R10 Signalements validés max avant retrait"));
        DEFAULTS.put(BusinessSettingsKeys.ORG_MIN_REVIEWS,
                new DefaultSetting("5", "R10 Nombre mini d'avis pour appliquer la note moyenne"));
        DEFAULTS.put(BusinessSettingsKeys.DOC_REMINDER_J30_DAYS,
                new DefaultSetting("30", "R16 Rappel expiration document J-30"));
        DEFAULTS.put(BusinessSettingsKeys.DOC_REMINDER_J7_DAYS,
                new DefaultSetting("7", "R16 Rappel expiration document J-7"));
    }

    private final SettingRepository settingRepository;

    public BusinessSettingsService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    public Set<String> knownKeys() {
        return DEFAULTS.keySet();
    }

    @Transactional(readOnly = true)
    public List<SettingResponse> listBusinessSettings() {
        return DEFAULTS.keySet().stream().map(this::getOrDefaultResponse).toList();
    }

    @Transactional(readOnly = true)
    public int getInt(String key) {
        DefaultSetting def = requireKnown(key);
        String raw = settingRepository.findById(key).map(Setting::getValue).orElse(def.defaultValue());
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return Integer.parseInt(def.defaultValue());
        }
    }

    @Transactional(readOnly = true)
    public BigDecimal getDecimal(String key) {
        return BigDecimal.valueOf(getInt(key));
    }

    @Transactional(readOnly = true)
    public int cancellationFreeHours() {
        return getInt(BusinessSettingsKeys.CANCELLATION_FREE_HOURS);
    }

    @Transactional(readOnly = true)
    public int pendingPaymentMinutes() {
        return getInt(BusinessSettingsKeys.PENDING_PAYMENT_MINUTES);
    }

    @Transactional(readOnly = true)
    public int waitlistOfferHours() {
        return getInt(BusinessSettingsKeys.WAITLIST_OFFER_HOURS);
    }

    @Transactional(readOnly = true)
    public int communityWalksThreshold() {
        return getInt(BusinessSettingsKeys.COMMUNITY_WALKS_THRESHOLD);
    }

    @Transactional(readOnly = true)
    public int communityCapacityMax() {
        return getInt(BusinessSettingsKeys.COMMUNITY_CAPACITY_MAX);
    }

    @Transactional(readOnly = true)
    public int indepCommissionPercent() {
        return getInt(BusinessSettingsKeys.INDEP_COMMISSION_PERCENT);
    }

    @Transactional(readOnly = true)
    public int reviewWindowDays() {
        return getInt(BusinessSettingsKeys.REVIEW_WINDOW_DAYS);
    }

    @Transactional(readOnly = true)
    public int orgMinRating() {
        return getInt(BusinessSettingsKeys.ORG_MIN_RATING);
    }

    @Transactional(readOnly = true)
    public int orgMaxReports() {
        return getInt(BusinessSettingsKeys.ORG_MAX_REPORTS);
    }

    @Transactional(readOnly = true)
    public int orgMinReviews() {
        return getInt(BusinessSettingsKeys.ORG_MIN_REVIEWS);
    }

    @Transactional(readOnly = true)
    public int docReminderJ30Days() {
        return getInt(BusinessSettingsKeys.DOC_REMINDER_J30_DAYS);
    }

    @Transactional(readOnly = true)
    public int docReminderJ7Days() {
        return getInt(BusinessSettingsKeys.DOC_REMINDER_J7_DAYS);
    }

    @Transactional
    public SettingResponse update(String key, String value) {
        requireKnown(key);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value required");
        }
        validateValue(key, value.trim());
        DefaultSetting def = DEFAULTS.get(key);
        Setting setting = settingRepository.findById(key).orElseGet(() -> {
            Setting s = new Setting();
            s.setKey(key);
            s.setDescription(def.description());
            return s;
        });
        setting.setValue(value.trim());
        if (setting.getDescription() == null || setting.getDescription().isBlank()) {
            setting.setDescription(def.description());
        }
        setting.setUpdatedAt(Instant.now());
        return toResponse(settingRepository.save(setting));
    }

    private SettingResponse getOrDefaultResponse(String key) {
        DefaultSetting def = DEFAULTS.get(key);
        return settingRepository.findById(key)
                .map(this::toResponse)
                .orElseGet(() -> new SettingResponse(key, def.defaultValue(), def.description(), null));
    }

    private DefaultSetting requireKnown(String key) {
        DefaultSetting def = DEFAULTS.get(key);
        if (def == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown business setting: " + key);
        }
        return def;
    }

    private void validateValue(String key, String value) {
        int v;
        try {
            v = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Integer value required for " + key);
        }
        if (v < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value cannot be negative");
        }
        if (BusinessSettingsKeys.INDEP_COMMISSION_PERCENT.equals(key) && v > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Commission percent max 100");
        }
        if (BusinessSettingsKeys.PENDING_PAYMENT_MINUTES.equals(key) && v == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pending_payment_minutes cannot be zero");
        }
        if (BusinessSettingsKeys.COMMUNITY_CAPACITY_MAX.equals(key) && v < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "community_capacity_max must be >= 1");
        }
        if (BusinessSettingsKeys.ORG_MIN_RATING.equals(key) && v > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "org_min_rating max 5");
        }
    }

    private SettingResponse toResponse(Setting s) {
        return new SettingResponse(s.getKey(), s.getValue(), s.getDescription(), s.getUpdatedAt());
    }

    private record DefaultSetting(String defaultValue, String description) {}
}
