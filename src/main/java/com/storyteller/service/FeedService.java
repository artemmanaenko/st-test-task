package com.storyteller.service;

import com.storyteller.dto.FeedItem;
import com.storyteller.dto.FeedResponse;
import com.storyteller.model.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    private final FeatureFlagService featureFlagService;
    private final RankingService rankingService;
    private final RedisTemplate<String, FeedResponse> feedCacheTemplate;

    private static final int CACHE_TTL_SECONDS = 45;

    public FeedResponse getFeed(UUID tenantId, String userIdHash, int limit) {
        log.info("Feed request | tenant={} user_hash={} limit={}", tenantId, userIdHash, limit);

        // Check feature flag
        boolean personalizedEnabled = featureFlagService.isPersonalizedEnabled(tenantId);
        log.info("Personalization enabled: {}", personalizedEnabled);

        // Try cache first
        String cacheKey = buildCacheKey(tenantId, userIdHash, limit, personalizedEnabled);
        FeedResponse cachedFeed = feedCacheTemplate.opsForValue().get(cacheKey);

        if (cachedFeed != null) {
            log.info("Cache HIT | key={}", cacheKey);
            return cachedFeed;
        }

        log.info("Cache MISS | key={}", cacheKey);

        List<Video> videos;
        if (personalizedEnabled) {
            videos = rankingService.rankVideos(tenantId, userIdHash, limit);
        } else {
            videos = rankingService.getFallbackFeed(limit);
        }

        List<FeedItem> items = videos.stream()
                .map(v -> new FeedItem(
                        v.getVideoId(),
                        v.getTitle(),
                        v.getUrl(),
                        v.getThumbnailUrl()))
                .collect(Collectors.toList());

        FeedResponse response = new FeedResponse(items, UUID.randomUUID().toString());

        feedCacheTemplate.opsForValue().set(cacheKey, response, CACHE_TTL_SECONDS, TimeUnit.SECONDS);
        log.info("Feed cached | key={} items={}", cacheKey, items.size());

        return response;
    }

    /**
     * Generate a weak ETag for fallback feeds so clients can re-use cached responses.
     * Format example: W/"fallback-<tenantId>-v1"
     */
    public String generateETag(UUID tenantId) {
        return String.format("W/\"fallback-%s-v1\"", tenantId);
    }

    private String buildCacheKey(UUID tenantId, String userIdHash, int limit, boolean personalized) {
        if (personalized) {
            return String.format("feed:%s:%s:%d:personalized", tenantId, userIdHash, limit);
        } else {
            return String.format("feed:%s:fallback:%d", tenantId, limit);
        }
    }
}
