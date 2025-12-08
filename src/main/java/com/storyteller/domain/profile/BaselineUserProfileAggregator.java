package com.storyteller.domain.profile;

import com.storyteller.domain.ranking.RankingDefaults;
import com.storyteller.dto.UserProfile;
import com.storyteller.model.UserEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default implementation for aggregating user events into tag scores.
 * Applies decay to existing scores and adds per-event increments.
 */
@Component
public class BaselineUserProfileAggregator implements IUserProfileAggregator {

    @Override
    public UserProfile aggregate(UserProfile current, List<UserEvent> events) {
        Map<String, Float> newScores = new HashMap<>();

        if (current != null && current.tagScores() != null) {
            current.tagScores().forEach((tag, score) ->
                    newScores.put(tag, score * RankingDefaults.PROFILE_DECAY_FACTOR));
        }

        for (UserEvent event : events) {
            List<String> videoTags = event.getVideo() != null ? event.getVideo().getTags() : null;
            if (videoTags == null) {
                continue;
            }

            float scoreIncrement = calculateScore(event);
            for (String tag : videoTags) {
                newScores.merge(tag, scoreIncrement, (a, b) -> a + b);
            }
        }

        String version = UUID.randomUUID().toString();
        return new UserProfile(newScores, version);
    }

    private float calculateScore(UserEvent event) {
        if (event.getEventType() == null) {
            return RankingDefaults.EVENT_SCORE_VIEW;
        }

        return switch (event.getEventType().toUpperCase()) {
            case "LIKE" -> RankingDefaults.EVENT_SCORE_LIKE;
            case "SHARE" -> RankingDefaults.EVENT_SCORE_SHARE;
            case "VIEW" -> RankingDefaults.EVENT_SCORE_VIEW;
            default -> RankingDefaults.EVENT_SCORE_VIEW;
        };
    }
}

