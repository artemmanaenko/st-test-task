package com.storyteller.service;

import com.storyteller.config.RankingDefaults;
import com.storyteller.dto.RankingWeights;
import com.storyteller.dto.UserProfile;
import com.storyteller.model.EditorialBoost;
import com.storyteller.model.Tenant;
import com.storyteller.model.Video;
import com.storyteller.repository.EditorialBoostRepository;
import com.storyteller.repository.TenantRepository;
import com.storyteller.repository.VideoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final VideoRepository videoRepository;
    private final TenantRepository tenantRepository;
    private final EditorialBoostRepository editorialBoostRepository;
    private final RedisTemplate<String, UserProfile> userProfileTemplate;

    public List<Video> rankVideos(UUID tenantId, String userIdHash, int limit) {
        log.info("Ranking videos | tenant={} user_hash={} limit={}", tenantId, userIdHash, limit);

        // Load tenant configuration
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        // Load user profile from Redis
        String profileKey = "profile:" + userIdHash;
        UserProfile userProfile = userProfileTemplate.opsForValue().get(profileKey);

        List<Video> allVideos = videoRepository.findAll();

        if (allVideos.isEmpty()) {
            log.warn("No videos found in database");
            return Collections.emptyList();
        }

        // Load active editorial boosts for all videos
        List<UUID> videoIds = allVideos.stream().map(Video::getVideoId).toList();
        List<EditorialBoost> activeBoosts = editorialBoostRepository.findActiveBoosts(videoIds, Instant.now());
        Map<UUID, EditorialBoost> boostMap = activeBoosts.stream()
                .collect(Collectors.toMap(EditorialBoost::getVideoId, boost -> boost));

        // Score and rank videos
        List<ScoredVideo> scoredVideos = allVideos.stream()
                .map(video -> scoreVideo(video, tenant, userProfile, boostMap.get(video.getVideoId())))
                .sorted(Comparator.comparingDouble(ScoredVideo::score).reversed())
                .limit(limit)
                .toList();

        log.info("Ranked {} videos | top_score={}", scoredVideos.size(),
                scoredVideos.isEmpty() ? 0 : scoredVideos.get(0).score());

        return scoredVideos.stream()
                .map(ScoredVideo::video)
                .collect(Collectors.toList());
    }

    public List<Video> getFallbackFeed(int limit) {
        log.info("Building fallback feed | limit={}", limit);

        // Simple popularity-based fallback
        List<Video> videos = videoRepository.findAll();

        return videos.stream()
                .sorted(Comparator.comparingDouble(Video::getPopularityScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private ScoredVideo scoreVideo(Video video, Tenant tenant, UserProfile userProfile, EditorialBoost boost) {
        // Extract ranking weights from tenant configuration or use defaults
        RankingWeights weights = tenant.getWeights();
        double wRecency = weights != null ? weights.recency() : RankingDefaults.RECENCY_WEIGHT;
        double wPopularity = weights != null ? weights.popularity() : RankingDefaults.POPULARITY_WEIGHT;
        double wAffinity = weights != null ? weights.userAffinity() : RankingDefaults.AFFINITY_WEIGHT;

        // Calculate component scores
        double recencyScore = calculateRecencyScore(video);
        double popularityScore = video.getPopularityScore() / RankingDefaults.POPULARITY_NORMALIZATION_FACTOR;
        double affinityScore = calculateAffinityScore(video, userProfile);

        // Weighted sum
        double totalScore = (wRecency * recencyScore) +
                (wPopularity * popularityScore) +
                (wAffinity * affinityScore);

        // Apply editorial boost (additive bonus)
        if (boost != null) {
            double boostValue = boost.getBoostFactor();
            totalScore += boostValue;
            log.debug("Applied editorial boost | video={} boost_factor={} final_score={}", 
                    video.getVideoId(), boostValue, totalScore);
        }

        return new ScoredVideo(video, totalScore);
    }

    private double calculateRecencyScore(Video video) {
        if (video.getCreatedAt() == null) {
            return RankingDefaults.DEFAULT_RECENCY_SCORE;
        }

        long daysSinceCreation = Duration.between(video.getCreatedAt(), Instant.now()).toDays();

        // Exponential decay: newer videos score higher
        // Score = 1.0 for today, decays over time based on config
        return Math.exp(-daysSinceCreation / RankingDefaults.RECENCY_DECAY_DAYS);
    }

    private double calculateAffinityScore(Video video, UserProfile userProfile) {
        if (userProfile == null || userProfile.tagScores() == null || video.getTags() == null) {
            return RankingDefaults.DEFAULT_AFFINITY_SCORE;
        }

        Map<String, Float> tagScores = userProfile.tagScores();
        List<String> videoTags = video.getTags();

        if (videoTags.isEmpty()) {
            return RankingDefaults.DEFAULT_AFFINITY_SCORE;
        }

        // Average score of matching tags (convert Float to double for calculation)
        double totalScore = videoTags.stream()
                .mapToDouble(tag -> tagScores.getOrDefault(tag, 0.0f).doubleValue())
                .average()
                .orElse(RankingDefaults.DEFAULT_AFFINITY_SCORE);

        // Normalize to 0-1 range (tag scores are configurable)
        return Math.min(totalScore / RankingDefaults.TAG_SCORE_NORMALIZATION_FACTOR, 1.0);
    }

    private record ScoredVideo(Video video, double score) {
    }
}
