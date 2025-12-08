package com.storyteller.domain.ranking;

import java.util.Map;

/**
 * Default configuration constants for ranking algorithm and tenant settings.
 * All magic numbers and default values centralized here for easy tuning.
 *
 * These are compile-time constants - if you need runtime configuration,
 * use tenant-specific weights stored in the database.
 */
public final class RankingDefaults {

    private RankingDefaults() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== RANKING WEIGHTS ====================

    public static final float RECENCY_WEIGHT = 0.3f;

    public static final float POPULARITY_WEIGHT = 0.4f;

    public static final float AFFINITY_WEIGHT = 0.3f;

    /**
     * Default weights as Map for tenant initialization.
     */
    public static final Map<String, Float> DEFAULT_WEIGHTS_MAP = Map.of(
            "recency", RECENCY_WEIGHT,
            "popularity", POPULARITY_WEIGHT,
            "user_affinity", AFFINITY_WEIGHT
    );

    /**
     * Default weights as JSONB string for database insertion.
     */
    public static final String DEFAULT_WEIGHTS_JSON =
            "{\"recency\": " + RECENCY_WEIGHT +
            ", \"popularity\": " + POPULARITY_WEIGHT +
            ", \"user_affinity\": " + AFFINITY_WEIGHT + "}";

    // ==================== SCORING PARAMETERS ====================


    public static final double DEFAULT_RECENCY_SCORE = 0.5;

    /**
     * Default score when user profile or video tags are missing
     */
    public static final double DEFAULT_AFFINITY_SCORE = 0.5;

    /**
     * Recency decay factor (in days).
     * Score = exp(-daysSinceCreation / decayDays)
     * Higher value = slower decay, videos stay "fresh" longer
     */
    public static final double RECENCY_DECAY_DAYS = 7.0;

    /**
     * Normalization factor for popularity score (assumes 0-100 scale)
     */
    public static final double POPULARITY_NORMALIZATION_FACTOR = 100.0;

    /**
     * Normalization factor for tag scores (assumes 0-10 scale)
     */
    public static final double TAG_SCORE_NORMALIZATION_FACTOR = 10.0;

    // ==================== USER PROFILE AGGREGATION ====================

    /**
     * Decay factor applied to existing tag scores during profile aggregation.
     * Each aggregation cycle, existing scores are multiplied by this factor.
     * Lower value = faster decay of old preferences.
     */
    public static final float PROFILE_DECAY_FACTOR = 0.9f;

    /**
     * Score added to tag preferences when user likes a video.
     */
    public static final float EVENT_SCORE_LIKE = 1.0f;

    /**
     * Score added to tag preferences when user shares a video.
     */
    public static final float EVENT_SCORE_SHARE = 2.0f;

    /**
     * Score added to tag preferences when user views a video.
     * Also used as default score for unknown event types.
     */
    public static final float EVENT_SCORE_VIEW = 0.1f;

    // ==================== TENANT DEFAULTS ====================

    /**
     * Default value for personalized_enabled flag when creating new tenants.
     * Also used as fallback when tenant is not found (fail-safe approach).
     * false = safer default (opt-in to personalization, fail-safe in case of errors)
     */
    public static final boolean DEFAULT_PERSONALIZED_ENABLED = false;

    /**
     * Fallback value for personalized_enabled when tenant is not found or flag is null.
     * Uses fail-safe approach: disable personalization by default to prevent issues.
     * Set to false to ensure system falls back to non-personalized feed in error cases.
     */
    public static final boolean FALLBACK_PERSONALIZED_ENABLED = false;
}

