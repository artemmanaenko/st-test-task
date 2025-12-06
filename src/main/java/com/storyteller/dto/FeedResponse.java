package com.storyteller.dto;

import java.util.List;

public record FeedResponse(
                List<FeedItem> items,
                String feedId) {
}
