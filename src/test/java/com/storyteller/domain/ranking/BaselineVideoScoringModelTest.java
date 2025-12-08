package com.storyteller.domain.ranking;

import com.storyteller.dto.RankingWeights;
import com.storyteller.dto.UserProfile;
import com.storyteller.model.EditorialBoost;
import com.storyteller.model.Tenant;
import com.storyteller.model.Video;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaselineVideoScoringModelTest {

    private final BaselineVideoScoringModel scoringModel = new BaselineVideoScoringModel();

    @Test
    void score_appliesWeightsAndBoost() {
        Tenant tenant = Tenant.builder()
                .tenantId(UUID.randomUUID())
                .weights(new RankingWeights(0.3f, 0.4f, 0.3f))
                .build();

        Video video = new Video();
        video.setVideoId(UUID.randomUUID());
        video.setCreatedAt(Instant.now());
        video.setPopularityScore(100.0);
        video.setTags(List.of("cats"));

        UserProfile profile = new UserProfile(Map.of("cats", 10.0f), "v1");
        EditorialBoost boost = EditorialBoost.builder().videoId(video.getVideoId()).boostFactor(0.5).build();

        double score = scoringModel.score(video, tenant, profile, boost);

        assertEquals(1.5, score, 1e-3); // 1.0 weighted sum + 0.5 boost
    }

    @Test
    void score_handlesMissingProfileOrCreatedAt() {
        Tenant tenant = Tenant.builder()
                .tenantId(UUID.randomUUID())
                .weights(new RankingWeights(0.3f, 0.4f, 0.3f))
                .build();

        Video video = new Video();
        video.setVideoId(UUID.randomUUID());
        video.setPopularityScore(50.0);
        video.setTags(List.of("unknown"));

        double score = scoringModel.score(video, tenant, null, null);

        assertEquals(0.5, score, 1e-3); // defaults used for recency and affinity
    }

    @Test
    void score_handlesNullPopularityAndZeroAffinity() {
        Tenant tenant = Tenant.builder()
                .tenantId(UUID.randomUUID())
                .weights(new RankingWeights(0.3f, 0.4f, 0.3f))
                .build();

        Video video = new Video();
        video.setVideoId(UUID.randomUUID());
        video.setCreatedAt(Instant.now());
        video.setTags(List.of("unknown"));

        UserProfile profile = new UserProfile(Map.of("other", 5.0f), "v1");

        double score = scoringModel.score(video, tenant, profile, null);

        assertTrue(score < 0.31); // recency contributes, popularity zero, affinity zero
    }
}

