package com.storyteller.service;

import com.storyteller.dto.FeedItem;
import com.storyteller.dto.FeedResponse;
import com.storyteller.model.Video;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FeedServiceTest {

    @Mock
    private FeatureFlagService featureFlagService;

    @Mock
    private RankingService rankingService;

    @Mock
    private RedisTemplate<String, FeedResponse> feedCacheTemplate;

    @Mock
    private ValueOperations<String, FeedResponse> valueOperations;

    @InjectMocks
    private FeedService feedService;

    private UUID tenantId;
    private String userIdHash;

    @BeforeEach
    void setUp() {
        tenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        userIdHash = "a".repeat(64);
        when(feedCacheTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void testGetFeed_Personalized_CacheMiss() {
        // Given
        when(featureFlagService.isPersonalizedEnabled(tenantId)).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn(null);

        Video video = new Video();
        video.setVideoId(UUID.randomUUID());
        video.setTitle("Test Video");
        video.setUrl("http://test.com");
        video.setPopularityScore(100.0);

        when(rankingService.rankVideos(eq(tenantId), eq(userIdHash), eq(10)))
                .thenReturn(List.of(video));

        // When
        FeedResponse response = feedService.getFeed(tenantId, userIdHash, 10);

        // Then
        assertNotNull(response);
        assertEquals(1, response.items().size());
        assertEquals("Test Video", response.items().get(0).title());

        verify(featureFlagService).isPersonalizedEnabled(tenantId);
        verify(rankingService).rankVideos(tenantId, userIdHash, 10);
        verify(valueOperations).set(anyString(), any(FeedResponse.class), anyLong(), any());
    }

    @Test
    void testGetFeed_Fallback() {
        // Given
        when(featureFlagService.isPersonalizedEnabled(tenantId)).thenReturn(false);
        when(valueOperations.get(anyString())).thenReturn(null);

        Video video = new Video();
        video.setVideoId(UUID.randomUUID());
        video.setTitle("Popular Video");
        video.setPopularityScore(200.0);

        when(rankingService.getFallbackFeed(10)).thenReturn(List.of(video));

        // When
        FeedResponse response = feedService.getFeed(tenantId, userIdHash, 10);

        // Then
        assertNotNull(response);
        assertEquals(1, response.items().size());
        verify(rankingService).getFallbackFeed(10);
        verify(rankingService, never()).rankVideos(any(), any(), anyInt());
    }

    @Test
    void testGetFeed_CacheHit() {
        // Given
        FeedItem cachedItem = new FeedItem(UUID.randomUUID(), "Cached", "http://cached.com", "http://cached.com/thumb.jpg");
        FeedResponse cachedResponse = new FeedResponse(List.of(cachedItem), "cached-id");

        when(featureFlagService.isPersonalizedEnabled(tenantId)).thenReturn(true);
        when(valueOperations.get(anyString())).thenReturn(cachedResponse);

        // When
        FeedResponse response = feedService.getFeed(tenantId, userIdHash, 10);

        // Then
        assertNotNull(response);
        assertEquals(cachedResponse, response);
        verify(rankingService, never()).rankVideos(any(), any(), anyInt());
        verify(rankingService, never()).getFallbackFeed(anyInt());
    }

    @Test
    void testGenerateETag() {
        // When
        String etag = feedService.generateETag(tenantId);

        // Then
        assertNotNull(etag);
        assertTrue(etag.startsWith("W/\"fallback-"));
        assertTrue(etag.contains(tenantId.toString()));
    }
}
