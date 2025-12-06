package com.storyteller.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "editorial_boosts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EditorialBoost {
    @Id
    @Column(name = "video_id")
    private UUID videoId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "video_id")
    private Video video;

    @Column(name = "boost_factor", nullable = false)
    private double boostFactor;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
