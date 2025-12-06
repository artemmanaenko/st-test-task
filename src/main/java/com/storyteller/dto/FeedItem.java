package com.storyteller.dto;

import java.util.UUID;

public record FeedItem(
        UUID videoId,
        String title,
        String url,
        String thumbnailUrl) {
}
