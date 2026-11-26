package com.barkwalk.admin;

// Clés de configuration métier (table settings) — pas de magie dans les services.
public final class BusinessSettingsKeys {
    private BusinessSettingsKeys() {}

    public static final String CANCELLATION_FREE_HOURS = "cancellation_free_hours";
    public static final String PENDING_PAYMENT_MINUTES = "pending_payment_minutes";
    public static final String WAITLIST_OFFER_HOURS = "waitlist_offer_hours";
    public static final String COMMUNITY_WALKS_THRESHOLD = "community_walks_threshold";
    public static final String COMMUNITY_CAPACITY_MAX = "community_capacity_max";
    public static final String INDEP_COMMISSION_PERCENT = "indep_commission_percent";
    public static final String REVIEW_WINDOW_DAYS = "review_window_days";
    public static final String ORG_MIN_RATING = "org_min_rating";
    public static final String ORG_MAX_REPORTS = "org_max_reports";
    public static final String ORG_MIN_REVIEWS = "org_min_reviews";
    public static final String DOC_REMINDER_J30_DAYS = "doc_reminder_j30_days";
    public static final String DOC_REMINDER_J7_DAYS = "doc_reminder_j7_days";
}
