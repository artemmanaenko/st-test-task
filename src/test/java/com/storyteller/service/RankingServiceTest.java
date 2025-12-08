package com.storyteller.service;

import com.storyteller.dto.RankingWeights;
import com.storyteller.dto.UserProfile;
import com.storyteller.model.Tenant;
import com.storyteller.model.Video;
import com.storyteller.repository.TenantRepository;
import com.storyteller.repository.EditorialBoostRepository;
import com.storyteller.repository.VideoRepository;
import com.storyteller.domain.ranking.IVideoScoringModel;
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
import org.springframework.lang.NonNull;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("NullAway") // Mockito stubbing triggers false positives for non-null parameters
class RankingServiceTest {

    @Mock
    private VideoRepository videoRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private EditorialBoostRepository editorialBoostRepository;

    @Mock
    private RedisTemplate<String, UserProfile> userProfileTemplate;

    @Mock
    private ValueOperations<String, UserProfile> valueOperations;

    @Mock
    private IVideoScoringModel videoScoringModel;

    @InjectMocks
    private RankingService rankingService;

    @NonNull
    private static final UUID TENANT_ID = Objects.requireNonNull(
            UUID.fromString("11111111-1111-1111-1111-111111111111"));
    private Tenant tenant;
    private List<Video> videos;

    @BeforeEach
    void setUp() {
        RankingWeights config = new RankingWeights(0.3f, 0.4f, 0.3f);
        tenant = Tenant.builder()
                .tenantId(TENANT_ID)
                .name("Test Tenant")
                .weights(config)
                .build();

        Video video1 = new Video();
        video1.setVideoId(UUID.randomUUID());
        video1.setTitle("Video 1");
        video1.setPopularityScore(100.0);
        video1.setTags(Arrays.asList("cats", "funny"));
        video1.setCreatedAt(Instant.now());

        Video video2 = new Video();
        video2.setVideoId(UUID.randomUUID());
        video2.setTitle("Video 2");
        video2.setPopularityScore(50.0);
        video2.setTags(Arrays.asList("cooking"));
        video2.setCreatedAt(Instant.now().minusSeconds(86400 * 7)); // 7 days ago

        videos = Arrays.asList(video1, video2);

        when(userProfileTemplate.opsForValue()).thenReturn(valueOperations);
        when(editorialBoostRepository.findActiveBoosts(anyList(), any())).thenReturn(Collections.emptyList());
        when(videoScoringModel.score(any(), any(), any(), any())).thenReturn(1.0);
    }

    @Test
    void testRankVideos_WithUserProfile() {
        // Given
        doReturn(Optional.of(tenant)).when(tenantRepository).findById(TENANT_ID);
        when(videoRepository.findAll()).thenReturn(videos);

        Map<String, Float> tagScores = new HashMap<>();
        tagScores.put("cats", 10.0f);
        tagScores.put("funny", 8.0f);
        UserProfile userProfile = new UserProfile(tagScores, "v1");

        doReturn(userProfile).when(valueOperations).get("profile:userhash");

        // When
        List<Video> rankedVideos = rankingService.rankVideos(TENANT_ID, "userhash", 10);

        // Then
        assertNotNull(rankedVideos);
        assertEquals(2, rankedVideos.size());
        verify(tenantRepository).findById(TENANT_ID);
        verify(videoRepository).findAll();
    }

    @Test
    void testRankVideos_NoUserProfile() {
        // Given
        doReturn(Optional.of(tenant)).when(tenantRepository).findById(TENANT_ID);
        when(videoRepository.findAll()).thenReturn(videos);
        doReturn(null).when(valueOperations).get("profile:userhash");

        // When
        List<Video> rankedVideos = rankingService.rankVideos(TENANT_ID, "userhash", 10);

        // Then
        assertNotNull(rankedVideos);
        assertEquals(2, rankedVideos.size());
    }

    @Test
    void testRankVideos_TenantNotFound() {
        // Given
        doReturn(Optional.empty()).when(tenantRepository).findById(TENANT_ID);

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            rankingService.rankVideos(TENANT_ID, "userhash", 10);
        });
    }

    @Test
    void testGetFallbackFeed() {
        // Given
        when(videoRepository.findAll()).thenReturn(videos);

        // When
        List<Video> fallbackFeed = rankingService.getFallbackFeed(10);

        // Then
        assertNotNull(fallbackFeed);
        assertEquals(2, fallbackFeed.size());
        // Should be sorted by popularity (descending)
        assertEquals(100.0, fallbackFeed.get(0).getPopularityScore());
        assertEquals(50.0, fallbackFeed.get(1).getPopularityScore());
    }

    @Test
    void testGetFallbackFeed_WithLimit() {
        // Given
        when(videoRepository.findAll()).thenReturn(videos);

        // When
        List<Video> fallbackFeed = rankingService.getFallbackFeed(1);

        // Then
        assertEquals(1, fallbackFeed.size());
        assertEquals(100.0, fallbackFeed.get(0).getPopularityScore());
    }

    @Test
    void testRankVideos_EmptyVideoList() {
        // Given
        doReturn(Optional.of(tenant)).when(tenantRepository).findById(TENANT_ID);
        when(videoRepository.findAll()).thenReturn(Collections.emptyList());

        // When
        List<Video> rankedVideos = rankingService.rankVideos(TENANT_ID, "userhash", 10);

        // Then
        assertNotNull(rankedVideos);
        assertTrue(rankedVideos.isEmpty());
    }
}
