package com.storyteller.service;

import com.storyteller.dto.UserProfile;
import com.storyteller.model.UserEvent;
import com.storyteller.model.Video;
import com.storyteller.repository.UserEventRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AggregationServiceTest {

    @Mock
    private UserEventRepository userEventRepository;

    @Mock
    private RedisTemplate<String, UserProfile> redisTemplate;

    @Mock
    private ValueOperations<String, UserProfile> valueOperations;

    @InjectMocks
    private AggregationService aggregationService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ReflectionTestUtils.setField(aggregationService, "intervalMs", 180000L);
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
        verify(valueOperations, atLeastOnce()).set(anyString(), any(UserProfile.class));
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
        verify(valueOperations, never()).set(anyString(), any());
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
        verify(valueOperations).set(eq("profile:user1hash"), any(UserProfile.class));
    }
}
