package com.storyteller.model;

import com.storyteller.dto.RankingWeights;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

@Entity
@Table(name = "tenants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {
    @Id
    @Column(name = "tenant_id")
    private UUID tenantId;

    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private RankingWeights weights;

    @Column(name = "maturity_filter")
    private String maturityFilter;

    @Column(name = "personalized_enabled")
    private Boolean personalizedEnabled;
}
