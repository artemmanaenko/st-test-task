package com.storyteller.dto;

import java.util.UUID;

/**
 * CMS webhook request for cache invalidation.
 * Sent when content, weights, or editorial boosts change in the CMS.
 */
public record CmsWebhookRequest(UUID tenantId) {}
