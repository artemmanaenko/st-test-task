package com.storyteller.service;

import com.storyteller.dto.UserProfile;
import com.storyteller.model.EditorialBoost;
import com.storyteller.model.Tenant;
import com.storyteller.model.Video;
import com.storyteller.repository.EditorialBoostRepository;
import com.storyteller.repository.TenantRepository;
import com.storyteller.repository.VideoRepository;
import com.storyteller.domain.ranking.IVideoScoringModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final VideoRepository videoRepository;
    private final TenantRepository tenantRepository;
    private final EditorialBoostRepository editorialBoostRepository;
    private final RedisTemplate<String, UserProfile> userProfileTemplate;
    private final IVideoScoringModel videoScoringModel;

    public List<Video> rankVideos(UUID tenantId, String userIdHash, int limit) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(userIdHash, "userIdHash must not be null");
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
        double score = videoScoringModel.score(video, tenant, userProfile, boost);
        return new ScoredVideo(video, score);
    }

    private record ScoredVideo(Video video, double score) {
    }
}
