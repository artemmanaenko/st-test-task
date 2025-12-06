package com.storyteller.controller;

import com.storyteller.AbstractIntegrationTest;
import com.storyteller.model.Tenant;
import com.storyteller.model.Video;
import com.storyteller.repository.TenantRepository;
import com.storyteller.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ETag functionality in FeedController.
 * Tests the critical caching mechanism for fallback feeds.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FeedControllerETagTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private VideoRepository videoRepository;

    private UUID tenantId;
    private String userIdHash;

    @BeforeEach
    void setUp() {
        // Create tenant with personalization DISABLED (fallback mode)
        tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .tenantId(tenantId)
                .name("ETag Test Tenant")
                .personalizedEnabled(false) // ✅ Fallback mode → ETag should work
                .build();
        tenantRepository.save(tenant);

        // Create test videos
        Video video1 = Video.builder()
                .videoId(UUID.randomUUID())
                .url("http://test.com/video1.mp4")
                .title("Test Video 1")
                .tags(List.of("test", "demo"))
                .popularityScore(100.0)
                .build();
        videoRepository.save(video1);

        userIdHash = "a".repeat(64); // Valid SHA-256 hash format
    }

    @Test
    void shouldReturnETagHeaderOnFirstRequest() throws Exception {
        // When: First request to fallback feed
        MvcResult result = mockMvc.perform(get("/v1/feed")
                        .param("tenantId", tenantId.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10"))
                // Then: Returns 200 with ETag
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().exists(HttpHeaders.CACHE_CONTROL))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=300, public"))
                .andReturn();

        // Verify ETag format
        String etag = result.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotNull();
        assertThat(etag).startsWith("W/\""); // Weak ETag
        assertThat(etag).contains("fallback");
        assertThat(etag).endsWith("\"");
    }

    @Test
    void shouldReturn304WhenETagMatches() throws Exception {
        // Given: First request to get ETag
        MvcResult firstResult = mockMvc.perform(get("/v1/feed")
                        .param("tenantId", tenantId.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();

        String etag = firstResult.getResponse().getHeader(HttpHeaders.ETAG);
        assertThat(etag).isNotNull();

        // When: Second request with If-None-Match header
        mockMvc.perform(get("/v1/feed")
                        .param("tenantId", tenantId.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10")
                        .header(HttpHeaders.IF_NONE_MATCH, etag)) // ✅ Send ETag back
                // Then: Returns 304 Not Modified
                .andExpect(status().isNotModified())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(header().string(HttpHeaders.ETAG, etag));
    }

    @Test
    void shouldReturn200WhenETagDoesNotMatch() throws Exception {
        // When: Request with invalid/old ETag
        mockMvc.perform(get("/v1/feed")
                        .param("tenantId", tenantId.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10")
                        .header(HttpHeaders.IF_NONE_MATCH, "W/\"old-etag-value\""))
                // Then: Returns 200 with new content
                .andExpect(status().isOk())
                .andExpect(header().exists(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void shouldNotReturnETagForPersonalizedFeed() throws Exception {
        // Given: Tenant with personalization ENABLED
        UUID personalizedTenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .tenantId(personalizedTenantId)
                .name("Personalized Tenant")
                .personalizedEnabled(true) // ✅ Personalized mode
                .build();
        tenantRepository.save(tenant);

        // When: Request personalized feed
        mockMvc.perform(get("/v1/feed")
                        .param("tenantId", personalizedTenantId.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10"))
                // Then: No ETag header (private cache)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=30, private"));
    }

    @Test
    void shouldHandleMultipleClientsWithSameETag() throws Exception {
        // Given: Get ETag
        MvcResult result = mockMvc.perform(get("/v1/feed")
                        .param("tenantId", tenantId.toString())
                        .param("userIdHash", userIdHash)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();

        String etag = result.getResponse().getHeader(HttpHeaders.ETAG);

        // When: Multiple clients request with same ETag
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/v1/feed")
                            .param("tenantId", tenantId.toString())
                            .param("userIdHash", "user" + i + "a".repeat(59)) // Different users
                            .param("limit", "10")
                            .header(HttpHeaders.IF_NONE_MATCH, etag))
                    // Then: All get 304 (same fallback feed)
                    .andExpect(status().isNotModified());
        }
    }

    @Test
    void shouldGenerateDifferentETagForDifferentTenants() throws Exception {
        // Given: Two tenants with fallback mode
        UUID tenant1 = tenantId;
        UUID tenant2 = UUID.randomUUID();
        Tenant secondTenant = Tenant.builder()
                .tenantId(tenant2)
                .name("Second Tenant")
                .personalizedEnabled(false)
                .build();
        tenantRepository.save(secondTenant);

        // When: Get ETags from both tenants
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

        // Then: ETags are different
        String etag1 = result1.getResponse().getHeader(HttpHeaders.ETAG);
        String etag2 = result2.getResponse().getHeader(HttpHeaders.ETAG);

        assertThat(etag1).isNotEqualTo(etag2);
        assertThat(etag1).contains(tenant1.toString().substring(0, 8));
        assertThat(etag2).contains(tenant2.toString().substring(0, 8));
    }
}

