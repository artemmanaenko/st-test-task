package com.storyteller.controller;

import com.storyteller.dto.FeedResponse;
import com.storyteller.service.FeedService;
import com.storyteller.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/v1/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;
    private final FeatureFlagService featureFlagService;

    @GetMapping
    public ResponseEntity<FeedResponse> getFeed(
            @RequestParam UUID tenantId,
            @RequestParam String userIdHash,
            @RequestParam(defaultValue = "20") int limit,
            WebRequest webRequest) {
        log.info("GET /v1/feed | tenant={} user_hash={} limit={}", tenantId, userIdHash, limit);

        boolean personalizedEnabled = featureFlagService.isPersonalizedEnabled(tenantId);

        // Handle ETag for fallback feeds
        if (!personalizedEnabled) {
            String etag = feedService.generateETag(tenantId);

            // Check If-None-Match header using Spring's built-in ETag support
            // This correctly handles ETag formatting (W/"..." vs "...")
            if (webRequest.checkNotModified(etag)) {
                log.info("ETag match | returning 304 Not Modified");
                return null;
            }

            FeedResponse response = feedService.getFeed(tenantId, userIdHash, limit);

            return ResponseEntity.ok()
                    .eTag(etag)
                    .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic())
                    .body(response);
        }

        // Personalized feed
        FeedResponse response = feedService.getFeed(tenantId, userIdHash, limit);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePrivate())
                .body(response);
    }
}
