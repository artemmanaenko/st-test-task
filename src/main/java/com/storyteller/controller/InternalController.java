package com.storyteller.controller;

import com.storyteller.dto.CmsWebhookRequest;
import com.storyteller.service.AggregationService;
import com.storyteller.service.FeedCacheService;
import com.storyteller.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalController {

    private final FeedCacheService feedCacheService;
    private final FeatureFlagService featureFlagService;
    private final AggregationService aggregationService;

    @PostMapping("/cms-webhook")
    public ResponseEntity<Void> invalidateTenantCache(@RequestBody CmsWebhookRequest request) {
        log.info("Received CMS webhook | tenant={}", request.tenantId());
        feedCacheService.invalidateTenant(request.tenantId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/kill-switch")
    public ResponseEntity<Void> killSwitch(@RequestParam boolean enabled) {
        log.info("Received kill-switch request: enabled={}", enabled);
        featureFlagService.updateAll(enabled);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tenant-flag")
    public ResponseEntity<Void> setTenantFlag(@RequestParam UUID tenantId, @RequestParam boolean enabled) {
        log.info("Received tenant-flag request for tenantId={}: enabled={}", tenantId, enabled);
        featureFlagService.updateTenant(tenantId, enabled);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/trigger-aggregation")
    public ResponseEntity<Map<String, Object>> triggerAggregation() {
        log.info("Received manual aggregation trigger request");
        int profilesUpdated = aggregationService.runAggregation();
        return ResponseEntity.ok(Map.of(
                "status", "completed",
                "profiles_updated", profilesUpdated
        ));
    }
}
