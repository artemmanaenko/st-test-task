package com.storyteller.controller;

import com.storyteller.dto.EventRequest;
import com.storyteller.model.UserEvent;
import com.storyteller.model.Video;
import com.storyteller.repository.UserEventRepository;
import com.storyteller.repository.VideoRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final UserEventRepository userEventRepository;
    private final VideoRepository videoRepository;

    @PostMapping
    public ResponseEntity<Void> ingestEvent(@Valid @RequestBody EventRequest request) {
        log.info("Event ingestion | tenant={} user_hash={} video={} type={}",
                request.tenantId(), request.userIdHash(), request.videoId(), request.eventType());

        Video video = videoRepository.findById(request.videoId())
                .orElseThrow(() -> new IllegalArgumentException("Video not found: " + request.videoId()));

        UserEvent event = UserEvent.builder()
                .eventId(UUID.randomUUID())
                .userIdHash(request.userIdHash())
                .video(video)
                .eventType(request.eventType())
                .metadata(request.metadata())
                .createdAt(Instant.now())
                .build();

        userEventRepository.save(event);

        log.info("Event saved | event_id={}", event.getEventId());

        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
