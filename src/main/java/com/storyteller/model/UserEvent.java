package com.storyteller.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "user_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEvent {
    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "user_id_hash", nullable = false, length = 64)
    private String userIdHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_id", nullable = false)
    private Video video;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
