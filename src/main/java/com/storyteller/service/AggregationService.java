package com.storyteller.service;

import com.storyteller.dto.UserProfile;
import com.storyteller.domain.profile.IUserProfileAggregator;
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
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class AggregationService {

    private final UserEventRepository userEventRepository;
    private final RedisTemplate<String, UserProfile> redisTemplate;
    private final IUserProfileAggregator userProfileAggregator;

    @Value("${aggregation.interval.ms:180000}")
    private long intervalMs;

    private static final String PROFILE_KEY_PREFIX = "profile:";

    // For tests/overrides without reflection
    void overrideIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

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

        UserProfile updatedProfile = Objects.requireNonNull(
                userProfileAggregator.aggregate(currentProfile, events),
                "Aggregator must return profile");
        redisTemplate.opsForValue().set(key, updatedProfile);
        log.debug("Updated profile for user {}", userIdHash);
    }
}
