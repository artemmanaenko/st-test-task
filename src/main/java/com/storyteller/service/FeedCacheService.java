package com.storyteller.service;

import com.storyteller.dto.FeedResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class FeedCacheService {

    private final RedisTemplate<String, FeedResponse> feedResponseRedisTemplate;

    // Constant for the Redis Key Pattern
    // feed:{tenantId}:{userIdHash}:{limit}
    private static final String FEED_KEY_PATTERN = "feed:%s:%s:%d";
    private static final String TENANT_KEY_PATTERN = "feed:%s:*";
    private static final Duration CACHE_TTL = Duration.ofSeconds(45);

    public Optional<FeedResponse> getFeed(UUID tenantId, String userIdHash, int limit) {
        String key = String.format(FEED_KEY_PATTERN, tenantId, userIdHash, limit);
        try {
            FeedResponse response = feedResponseRedisTemplate.opsForValue().get(key);
            if (response != null) {
                log.debug("Cache HIT for key: {}", key);
                return Optional.of(response);
            }
        } catch (Exception e) {
            log.warn("Failed to read from cache", e);
        }
        log.debug("Cache MISS for key: {}", key);
        return Optional.empty();
    }

    public void saveFeed(UUID tenantId, String userIdHash, int limit, FeedResponse response) {
        String key = String.format(FEED_KEY_PATTERN, tenantId, userIdHash, limit);
        try {
            feedResponseRedisTemplate.opsForValue().set(key, response, CACHE_TTL);
            log.debug("Cached feed for key: {}", key);
        } catch (Exception e) {
            log.warn("Failed to write to cache", e);
        }
    }

    public void invalidateTenant(UUID tenantId) {
        String pattern = String.format(TENANT_KEY_PATTERN, tenantId);
        try {
            Set<String> keys = feedResponseRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                feedResponseRedisTemplate.delete(keys);
                log.info("Invalidated {} keys for tenant {}", keys.size(), tenantId);
            } else {
                log.info("No keys found to invalidate for tenant {}", tenantId);
            }
        } catch (Exception e) {
            log.error("Failed to invalidate cache for tenant {}", tenantId, e);
        }
    }
}
