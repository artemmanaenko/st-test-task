package com.storyteller.service;

import com.storyteller.dto.UserProfile;
import com.storyteller.domain.profile.IUserProfileAggregator;
import com.storyteller.model.UserEvent;
import com.storyteller.model.Video;
import com.storyteller.repository.UserEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Objects;
import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings({"NullAway", "Nullness"}) // Mockito stubbing uses nullable matchers
class AggregationServiceTest {

    @Mock
    private UserEventRepository userEventRepository;

    @Mock
    private RedisTemplate<String, UserProfile> redisTemplate;

    @Mock
    private ValueOperations<String, UserProfile> valueOperations;

    @Mock
    private IUserProfileAggregator userProfileAggregator;

    @SuppressWarnings("NullAway")
    private AggregationService aggregationService;

    @BeforeEach
    @SuppressWarnings("NullAway")
    void setUp() {
        aggregationService = new AggregationService(
                Objects.requireNonNull(userEventRepository),
                Objects.requireNonNull(redisTemplate),
                Objects.requireNonNull(userProfileAggregator));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        aggregationService.overrideIntervalMs(180000L);
        when(userProfileAggregator.aggregate(any(), anyList()))
                .thenReturn(new UserProfile(new HashMap<>(), "vX"));
    }

    @Test
    void testAggregateProfiles_WithEvents() {

        Video video1 = new Video();
        video1.setTags(Arrays.asList("cats", "funny"));
        
        Video video2 = new Video();
        video2.setTags(Arrays.asList("cooking", "food"));

        UserEvent event1 = UserEvent.builder()
                .userIdHash("user1hash")
                .video(video1)
                .eventType("VIEW")
                .build();

        UserEvent event2 = UserEvent.builder()
                .userIdHash("user1hash")
                .video(video2)
                .eventType("VIEW")
                .build();

        when(userEventRepository.findRecentEvents(any(Instant.class)))
                .thenReturn(Arrays.asList(event1, event2));

        // When
        aggregationService.aggregateProfiles();

        // Then
        verify(userEventRepository).findRecentEvents(any(Instant.class));
        verify(userProfileAggregator, atLeastOnce()).aggregate(any(), anyList());
    }

    @Test
    void testAggregateProfiles_NoEvents() {
        // Given
        when(userEventRepository.findRecentEvents(any(Instant.class)))
                .thenReturn(Collections.emptyList());

        // When
        aggregationService.aggregateProfiles();

        // Then
        verify(userEventRepository).findRecentEvents(any(Instant.class));
        verify(userProfileAggregator, never()).aggregate(any(), anyList());
    }

    @Test
    void testAggregateProfiles_UpdatesExistingProfile() {
        // Given
        Video video = new Video();
        video.setTags(Arrays.asList("cats"));

        UserEvent event = UserEvent.builder()
                .userIdHash("user1hash")
                .video(video)
                .eventType("LIKE")
                .build();

        Map<String, Float> existingScores = new HashMap<>();
        existingScores.put("dogs", 5.0f);
        UserProfile existingProfile = new UserProfile(existingScores, "v1");

        when(userEventRepository.findRecentEvents(any(Instant.class)))
                .thenReturn(List.of(event));
        when(valueOperations.get("profile:user1hash")).thenReturn(existingProfile);

        // When
        aggregationService.aggregateProfiles();

        // Then
        verify(valueOperations).get("profile:user1hash");
        verify(userProfileAggregator).aggregate(any(), anyList());
    }
}
