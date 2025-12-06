package com.storyteller.repository;

import com.storyteller.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    @Modifying
    @Transactional
    @Query("UPDATE Tenant t SET t.personalizedEnabled = :enabled")
    void updateAllPersonalizedEnabled(boolean enabled);

    @Modifying
    @Transactional
    @Query("UPDATE Tenant t SET t.personalizedEnabled = :enabled WHERE t.tenantId = :tenantId")
    void updatePersonalizedEnabled(UUID tenantId, boolean enabled);
}
