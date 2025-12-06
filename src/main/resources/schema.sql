-- Full schema for Personalised Video Feeds
-- Cleanup for re-running
DROP TABLE IF EXISTS editorial_boosts;
DROP TABLE IF EXISTS user_events CASCADE;
DROP TABLE IF EXISTS videos;
DROP TABLE IF EXISTS tenants;

CREATE TABLE tenants (
    tenant_id            UUID PRIMARY KEY,
    name                 TEXT NOT NULL,
    weights              JSONB NOT NULL, -- See RankingDefaults.DEFAULT_WEIGHTS_JSON
    maturity_filter      TEXT,
    personalized_enabled BOOLEAN NOT NULL DEFAULT false, -- See RankingDefaults.DEFAULT_PERSONALIZED_ENABLED
    created_at           TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE videos (
    video_id         UUID PRIMARY KEY,
    url              TEXT,
    title            TEXT,
    thumbnail_url    TEXT,
    tags             TEXT[],
    maturity_rating  TEXT,
    popularity_score DOUBLE PRECISION DEFAULT 0,
    created_at       TIMESTAMP WITH TIME ZONE DEFAULT now()
);

CREATE TABLE user_events (
    event_id      UUID DEFAULT gen_random_uuid(),
    user_id_hash  VARCHAR(64) NOT NULL CHECK (length(user_id_hash) = 64),
    video_id      UUID NOT NULL REFERENCES videos(video_id),
    event_type    VARCHAR(50) NOT NULL,
    metadata      JSONB,
    created_at    TIMESTAMP WITH TIME ZONE DEFAULT now(),
    PRIMARY KEY (event_id, created_at)
) PARTITION BY RANGE (created_at);

-- Partition for December 2025
CREATE TABLE user_events_2025_12 PARTITION OF user_events
    FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

CREATE TABLE editorial_boosts (
    video_id     UUID REFERENCES videos(video_id),
    boost_factor DOUBLE PRECISION NOT NULL DEFAULT 5.0,
    expires_at   TIMESTAMP WITH TIME ZONE,
    valid_from   TIMESTAMP WITH TIME ZONE DEFAULT now(),
    PRIMARY KEY (video_id)
);
