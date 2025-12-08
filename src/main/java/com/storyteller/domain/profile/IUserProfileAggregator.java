package com.storyteller.domain.profile;

import com.storyteller.dto.UserProfile;
import com.storyteller.model.UserEvent;

import java.util.List;

/**
 * Aggregates user events into an updated profile.
 * Allows swapping strategies (e.g., different decay/scoring) per tenant or experiment.
 */
public interface IUserProfileAggregator {

    /**
     * Aggregate events into a new profile.
     * @param current existing profile (can be null)
     * @param events events for the user (non-null, can be empty)
     * @return updated profile
     */
    UserProfile aggregate(UserProfile current, List<UserEvent> events);
}

