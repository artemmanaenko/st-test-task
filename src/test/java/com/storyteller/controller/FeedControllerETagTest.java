package com.storyteller.controller;

import com.storyteller.dto.FeedItem;
import com.storyteller.dto.FeedResponse;
import com.storyteller.service.FeedService;
import com.storyteller.service.FeatureFlagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer tests for ETag functionality in FeedController.
 * Uses mocked services to avoid Testcontainers/Docker dependencies.
 */
@WebMvcTest(controllers = FeedController.class)
@AutoConfigureMockMvc
@Import(FeedControllerETagTest.MockConfig.class)
class FeedControllerETagTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FeedService feedService;

    @Autowired
    private FeatureFlagService featureFlagService;

    private UUID tenantId;
    private String userIdHash;
    private FeedResponse fallbackResponse;
    private String etag;

    @BeforeEach
    void setUp() {
        Mockito.reset(feedService, featureFlagService);
        tenantId = UUID.randomUUID();
        userIdHash = "a".repeat(64); // Valid SHA-256 hash format

        FeedItem item = new FeedItem(UUID.randomUUID(), "Test Video 1", "http://test.com/video1.mp4", "http://test.com/thumb1.jpg");
        fallbackResponse = new FeedResponse(List.of(item), "feed-id-1");
        etag = String.format("W/\"fallback-%s-v1\"", tenantId);

        given(featureFlagService.isPersonalizedEnabled(any(UUID.class))).willReturn(false);
        given(feedService.getFeed(any(UUID.class), anyString(), anyInt())).willReturn(fallbackResponse);
        given(feedService.generateETag(eq(tenantId))).willReturn(etag);
    }

    @Test
    void shouldReturnETagHeaderOnFirstRequest() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/feed")
                        .param("tenantId", tenantId.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().exists(HttpHeaders.CACHE_CONTROL))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, public"))
                .andReturn();

        String etag = result.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotNull();
        assertThat(etag).startsWith("W/\""); // Weak ETag
        assertThat(etag).contains("fallback");
        assertThat(etag).endsWith("\"");
    }

    @Test
    void shouldReturn304WhenETagMatches() throws Exception {
        MvcResult firstResult = mockMvc.perform(get("/v1/feed")
                        .param("tenantId", tenantId.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();

        String etag = firstResult.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotNull();

        mockMvc.perform(get("/v1/feed")
                        .param("tenantId", tenantId.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10")
                        .header(HttpHeaders.IF_NONE_MATCH, etag))
                .andExpect(status().isNotModified())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    @Test
    void shouldReturn200WhenETagDoesNotMatch() throws Exception {
        mockMvc.perform(get("/v1/feed")
                        .param("tenantId", tenantId.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10")
                        .header(HttpHeaders.IF_NONE_MATCH, "W/\"old-etag-value\""))
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void shouldNotReturnETagForPersonalizedFeed() throws Exception {
        UUID personalizedTenantId = UUID.randomUUID();
        given(featureFlagService.isPersonalizedEnabled(eq(personalizedTenantId))).willReturn(true);

        mockMvc.perform(get("/v1/feed")
                        .param("tenantId", personalizedTenantId.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=30, private"));
    }

    @Test
    void shouldHandleMultipleClientsWithSameETag() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/feed")
                        .param("tenantId", tenantId.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();

        String etag = result.getResponse().getHeader(HttpHeaders.ETAG);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/v1/feed")
                            .param("tenantId", tenantId.toString())
                            .param("userIdHash", "user" + i + "a".repeat(59))
                            .param("limit", "10")
                            .header(HttpHeaders.IF_NONE_MATCH, etag))
                    .andExpect(status().isNotModified());
        }
    }

    @Test
    void shouldGenerateDifferentETagForDifferentTenants() throws Exception {
        UUID tenant1 = tenantId;
        UUID tenant2 = UUID.randomUUID();
        String expectedEtag2 = String.format("W/\"fallback-%s-v1\"", tenant2);
        given(feedService.generateETag(eq(tenant2))).willReturn(expectedEtag2);

        MvcResult result1 = mockMvc.perform(get("/v1/feed")
                        .param("tenantId", tenant1.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();

        MvcResult result2 = mockMvc.perform(get("/v1/feed")
                        .param("tenantId", tenant2.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();

        String etag1 = result1.getResponse().getHeader(HttpHeaders.ETAG);
        String etag2 = result2.getResponse().getHeader(HttpHeaders.ETAG);

        assertThat(etag1).isNotEqualTo(etag2);
        assertThat(etag1).contains(tenant1.toString().substring(0, 8));
        assertThat(etag2).contains(tenant2.toString().substring(0, 8));
    }

    @TestConfiguration
    static class MockConfig {
        @Bean
        FeedService feedService() {
            return Mockito.mock(FeedService.class);
        }

        @Bean
        FeatureFlagService featureFlagService() {
            return Mockito.mock(FeatureFlagService.class);
        }
    }
}

