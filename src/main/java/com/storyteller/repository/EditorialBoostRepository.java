package com.storyteller.repository;

import com.storyteller.model.EditorialBoost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface EditorialBoostRepository extends JpaRepository<EditorialBoost, UUID> {
    
    /**
     * Find active editorial boost for a video.
     * Boost is active if:
     * - expires_at is NULL (never expires), OR
     * - expires_at is in the future
     */
    @Query("SELECT eb FROM EditorialBoost eb WHERE eb.videoId = :videoId " +
           "AND (eb.expiresAt IS NULL OR eb.expiresAt > :now)")
    EditorialBoost findActiveBoost(@Param("videoId") UUID videoId, @Param("now") Instant now);
    
    /**
     * Find all active editorial boosts for multiple videos.
     */
    @Query("SELECT eb FROM EditorialBoost eb WHERE eb.videoId IN :videoIds " +
           "AND (eb.expiresAt IS NULL OR eb.expiresAt > :now)")
    List<EditorialBoost> findActiveBoosts(@Param("videoIds") List<UUID> videoIds, @Param("now") Instant now);
}

