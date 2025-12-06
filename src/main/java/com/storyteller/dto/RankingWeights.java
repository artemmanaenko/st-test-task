package com.storyteller.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ranking weights for personalized video feed algorithm.
 * Used to configure how recency, popularity, and user affinity contribute to video scores.
 * Uses float for memory efficiency (consistent with UserProfile.tagScores).
 */
public record RankingWeights(
                float recency,
                float popularity,
                @JsonProperty("user_affinity") float userAffinity) {
}
