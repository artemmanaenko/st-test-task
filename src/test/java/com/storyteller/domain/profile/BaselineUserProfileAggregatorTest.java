package com.storyteller.domain.profile;

import com.storyteller.config.RankingDefaults;
import com.storyteller.dto.UserProfile;
import com.storyteller.model.UserEvent;
import com.storyteller.model.Video;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaselineUserProfileAggregatorTest {

    private final BaselineUserProfileAggregator aggregator = new BaselineUserProfileAggregator();

    @Test
    void aggregatesWithDecayAndViewScore() {
        Map<String, Float> existing = new HashMap<>();
        existing.put("cats", 2.0f);
        UserProfile current = new UserProfile(existing, "v1");

        Video video = Video.builder().tags(List.of("cats")).build();
        UserEvent event = UserEvent.builder()
                .eventType("VIEW")
                .video(video)
                .build();

        UserProfile result = aggregator.aggregate(current, List.of(event));

        float expected = (float) (2.0f * RankingDefaults.PROFILE_DECAY_FACTOR + RankingDefaults.EVENT_SCORE_VIEW);
        assertEquals(expected, result.tagScores().get("cats"), 1e-4);
    }

    @Test
    void aggregatesNewTagsWithLikeScore() {
        Video video = Video.builder().tags(List.of("dogs", "pets")).build();
        UserEvent event = UserEvent.builder()
                .eventType("LIKE")
                .video(video)
                .build();

        UserProfile result = aggregator.aggregate(null, List.of(event));

        assertEquals(RankingDefaults.EVENT_SCORE_LIKE, result.tagScores().get("dogs"), 1e-4);
        assertEquals(RankingDefaults.EVENT_SCORE_LIKE, result.tagScores().get("pets"), 1e-4);
    }

    @Test
    void skipsEventsWithoutTags() {
        UserEvent event = UserEvent.builder()
                .eventType("VIEW")
                .video(Video.builder().tags(null).build())
                .build();

        UserProfile result = aggregator.aggregate(null, List.of(event));

        assertTrue(result.tagScores().isEmpty());
    }
}

