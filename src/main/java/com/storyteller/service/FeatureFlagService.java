package com.storyteller.service;

import com.storyteller.domain.ranking.RankingDefaults;
import com.storyteller.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureFlagService {

    private final TenantRepository tenantRepository;

    @Cacheable("featureFlags")
    public boolean isPersonalizedEnabled(UUID tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        log.debug("Checking personalization flag for tenant {}", tenantId);
        return tenantRepository.findById(tenantId)
                .map(t -> {
                    Boolean enabled = t.getPersonalizedEnabled();
                    // If null, use default; if explicitly false, return false; if true, return true
                    return enabled != null ? enabled : RankingDefaults.DEFAULT_PERSONALIZED_ENABLED;
                })
                .orElse(RankingDefaults.FALLBACK_PERSONALIZED_ENABLED); // Fail-safe: disable if tenant not found
    }

    @org.springframework.cache.annotation.CacheEvict(value = "featureFlags", allEntries = true)
    public void updateAll(boolean enabled) {
        log.info("Updating all tenants personalization flag to: {}", enabled);
        tenantRepository.updateAllPersonalizedEnabled(enabled);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "featureFlags", key = "#tenantId")
    public void updateTenant(UUID tenantId, boolean enabled) {
        log.info("Updating tenant {} personalization flag to: {}", tenantId, enabled);
        tenantRepository.updatePersonalizedEnabled(tenantId, enabled);
    }
}
