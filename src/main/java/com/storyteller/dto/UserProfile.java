package com.storyteller.dto;

import java.util.Map;

public record UserProfile(
        Map<String, Float> tagScores,
        String version) {
}
