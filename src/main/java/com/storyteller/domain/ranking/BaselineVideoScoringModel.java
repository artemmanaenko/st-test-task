package com.storyteller.domain.ranking;

import com.storyteller.config.RankingDefaults;
import com.storyteller.dto.RankingWeights;
import com.storyteller.dto.UserProfile;
import com.storyteller.model.EditorialBoost;
import com.storyteller.model.Tenant;
import com.storyteller.model.Video;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Default scoring model used for ranking videos.
 * Keeps the math isolated from RankingService so it can be tested and swapped.
 */
@Component
@Slf4j
public class BaselineVideoScoringModel implements IVideoScoringModel {

    @Override
    public double score(Video video, Tenant tenant, UserProfile userProfile, EditorialBoost boost) {
        RankingWeights weights = tenant.getWeights();
        double wRecency = weights != null ? weights.recency() : RankingDefaults.RECENCY_WEIGHT;
        double wPopularity = weights != null ? weights.popularity() : RankingDefaults.POPULARITY_WEIGHT;
        double wAffinity = weights != null ? weights.userAffinity() : RankingDefaults.AFFINITY_WEIGHT;

        double recencyScore = calculateRecencyScore(video);
        double popularityScore = normalizePopularity(video.getPopularityScore());
        double affinityScore = calculateAffinityScore(video, userProfile);

        double totalScore = (wRecency * recencyScore) +
                (wPopularity * popularityScore) +
                (wAffinity * affinityScore);

        if (boost != null) {
            double boostValue = boost.getBoostFactor();
            totalScore += boostValue;
            log.debug("Applied editorial boost | video={} boost_factor={} final_score={}",
                    video.getVideoId(), boostValue, totalScore);
        }

        return totalScore;
    }

    double calculateRecencyScore(Video video) {
        if (video.getCreatedAt() == null) {
            return RankingDefaults.DEFAULT_RECENCY_SCORE;
        }

        long daysSinceCreation = Duration.between(video.getCreatedAt(), Instant.now()).toDays();
        return Math.exp(-daysSinceCreation / RankingDefaults.RECENCY_DECAY_DAYS);
    }

    double calculateAffinityScore(Video video, UserProfile userProfile) {
        if (userProfile == null || userProfile.tagScores() == null || video.getTags() == null) {
            return RankingDefaults.DEFAULT_AFFINITY_SCORE;
        }

        Map<String, Float> tagScores = userProfile.tagScores();
        List<String> videoTags = video.getTags();

        if (videoTags.isEmpty()) {
            return RankingDefaults.DEFAULT_AFFINITY_SCORE;
        }

        double totalScore = videoTags.stream()
                .mapToDouble(tag -> tagScores.getOrDefault(tag, 0.0f).doubleValue())
                .average()
                .orElse(RankingDefaults.DEFAULT_AFFINITY_SCORE);

        return Math.min(totalScore / RankingDefaults.TAG_SCORE_NORMALIZATION_FACTOR, 1.0);
    }

    private double normalizePopularity(Double popularityScore) {
        if (popularityScore == null) {
            return 0.0;
        }
        return popularityScore / RankingDefaults.POPULARITY_NORMALIZATION_FACTOR;
    }
}

