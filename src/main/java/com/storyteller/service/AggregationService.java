package com.storyteller.service;

import com.storyteller.config.RankingDefaults;
import com.storyteller.dto.UserProfile;
import com.storyteller.model.UserEvent;
import com.storyteller.repository.UserEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AggregationService {

    private final UserEventRepository userEventRepository;
    private final RedisTemplate<String, UserProfile> redisTemplate;

    @Value("${aggregation.interval.ms:180000}")
    private long intervalMs;

    private static final String PROFILE_KEY_PREFIX = "profile:";

    /**
     * Aggregate user events into profiles (scheduled every 3 minutes).
     */
    @Scheduled(fixedDelayString = "${aggregation.interval.ms:180000}")
    @Transactional(readOnly = true)
    public void aggregateProfiles() {
        log.info("Starting user profile aggregation job...");
        int updatedCount = runAggregation();
        log.info("Aggregation job completed. Processed {} users.", updatedCount);
    }

    /**
     * Manual aggregation trigger (for testing or admin operations).
     * @return number of users with updated profiles
     */
    public int runAggregation() {
        Instant since = Instant.now().minusMillis(intervalMs + 60000); // interval + 1 min buffer

        List<UserEvent> events = userEventRepository.findRecentEvents(since);
        if (events.isEmpty()) {
            log.info("No recent events to process.");
            return 0;
        }

        Map<String, List<UserEvent>> eventsByUser = events.stream()
                .collect(Collectors.groupingBy(UserEvent::getUserIdHash));

        eventsByUser.forEach(this::updateUserProfile);
        return eventsByUser.size();
    }

    private void updateUserProfile(String userIdHash, List<UserEvent> events) {
        String key = PROFILE_KEY_PREFIX + userIdHash;
        UserProfile currentProfile = redisTemplate.opsForValue().get(key);

        Map<String, Float> newScores = new HashMap<>();
        if (currentProfile != null && currentProfile.tagScores() != null) {
            currentProfile.tagScores().forEach((tag, score) -> 
                    newScores.put(tag, score * RankingDefaults.PROFILE_DECAY_FACTOR));
        }

        for (UserEvent event : events) {
            List<String> videoTags = event.getVideo().getTags();
            if (videoTags != null) {
                float score = calculateScore(event);
                for (String tag : videoTags) {
                    newScores.merge(tag, score, Float::sum);
                }
            }
        }

        String newVersion = UUID.randomUUID().toString();
        UserProfile updatedProfile = new UserProfile(newScores, newVersion);
        redisTemplate.opsForValue().set(key, updatedProfile);
        log.debug("Updated profile for user {}", userIdHash);
    }

    private float calculateScore(UserEvent event) {
        return switch (event.getEventType().toUpperCase()) {
            case "LIKE" -> RankingDefaults.EVENT_SCORE_LIKE;
            case "SHARE" -> RankingDefaults.EVENT_SCORE_SHARE;
            case "VIEW" -> RankingDefaults.EVENT_SCORE_VIEW;
            default -> RankingDefaults.EVENT_SCORE_VIEW;
        };
    }
}
