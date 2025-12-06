package com.storyteller.service;

import com.storyteller.dto.FeedResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedCacheServiceTest {

    @Mock
    private RedisTemplate<String, FeedResponse> feedResponseRedisTemplate;

    @Mock
    private ValueOperations<String, FeedResponse> valueOperations;

    @InjectMocks
    private FeedCacheService feedCacheService;

    private UUID tenantId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(feedResponseRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testGetFeed_CacheHit() {
        // Given
        String userHash = "a".repeat(64);
        FeedResponse cachedResponse = new FeedResponse(List.of(), "test-id");
        when(valueOperations.get(anyString())).thenReturn(cachedResponse);

        // When
        Optional<FeedResponse> result = feedCacheService.getFeed(tenantId, userHash, 10);

        // Then
        assertTrue(result.isPresent());
        assertEquals(cachedResponse, result.get());
        verify(valueOperations).get(anyString());
    }

    @Test
    void testGetFeed_CacheMiss() {
        // Given
        String userHash = "a".repeat(64);
        when(valueOperations.get(anyString())).thenReturn(null);

        // When
        Optional<FeedResponse> result = feedCacheService.getFeed(tenantId, userHash, 10);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testSaveFeed() {
        // Given
        String userHash = "a".repeat(64);
        FeedResponse response = new FeedResponse(List.of(), "test-id");

        // When
        feedCacheService.saveFeed(tenantId, userHash, 10, response);

        // Then
        verify(valueOperations).set(anyString(), eq(response), any());
    }

    @Test
    void testInvalidateTenant() {
        // Given
        Set<String> keys = Set.of(
                "feed:" + tenantId + ":user1:10",
                "feed:" + tenantId + ":user2:10");
        when(feedResponseRedisTemplate.keys(anyString())).thenReturn(keys);

        // When
        feedCacheService.invalidateTenant(tenantId);

        // Then
        verify(feedResponseRedisTemplate).keys("feed:" + tenantId + ":*");
        verify(feedResponseRedisTemplate).delete(keys);
    }

    @Test
    void testInvalidateTenant_NoKeys() {
        // Given
        when(feedResponseRedisTemplate.keys(anyString())).thenReturn(Set.of());

        // When
        feedCacheService.invalidateTenant(tenantId);

        // Then
        verify(feedResponseRedisTemplate).keys(anyString());
        verify(feedResponseRedisTemplate, never()).delete(any(Set.class));
    }
}
