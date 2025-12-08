package com.storyteller.domain.ranking;

import com.storyteller.dto.UserProfile;
import com.storyteller.model.EditorialBoost;
import com.storyteller.model.Tenant;
import com.storyteller.model.Video;

/**
 * Strategy interface for scoring a single video.
 * Allows swapping implementations for experiments or A/B tests.
 */
public interface IVideoScoringModel {

    /**
     * Calculate the score for a video given context.
     */
    double score(Video video, Tenant tenant, UserProfile userProfile, EditorialBoost boost);
}

