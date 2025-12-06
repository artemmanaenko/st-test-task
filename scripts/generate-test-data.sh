#!/bin/bash
# generate-test-data.sh - Generates realistic test data for Personalised Video Feeds prototype
# Run after: docker compose up --build

DB_CONTAINER=$(docker ps --filter "name=storyteller-task-db" --format "{{.Names}}" | head -1)

if [ -z "$DB_CONTAINER" ]; then
  echo "Error: Database container not found. Make sure docker compose is running."
  exit 1
fi

echo "Generating realistic test data..."

docker exec -i $DB_CONTAINER psql -U postgres -d storyteller <<'SQL'
-- Enable pgcrypto for digest function
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Clean existing data
DELETE FROM user_events;
DELETE FROM videos;
DELETE FROM tenants;

-- 5 Tenants with different configurations
INSERT INTO tenants (tenant_id, name, weights, maturity_filter, personalized_enabled) VALUES
('11111111-1111-1111-1111-111111111111', 'Kids TV',          '{"recency": 0.1, "popularity": 0.2, "user_affinity": 0.7}', 'G',     true),
('22222222-2222-2222-2222-222222222222', 'Sports Hub',       '{"recency": 0.4, "popularity": 0.5, "user_affinity": 0.1}', NULL,    true),
('33333333-3333-3333-3333-333333333333', 'Cooking Pro',      '{"recency": 0.2, "popularity": 0.3, "user_affinity": 0.5}', NULL,    true),
('44444444-4444-4444-4444-444444444444', 'General Audience', '{"recency": 0.3, "popularity": 0.4, "user_affinity": 0.3}', NULL,    true),
('55555555-5555-5555-5555-555555555555', 'No Personalisation','{"recency": 0.3, "popularity": 0.7, "user_affinity": 0.0}', NULL, false);

-- 30 realistic videos with meaningful tags
INSERT INTO videos (video_id, title, url, tags, maturity_rating, popularity_score, created_at) VALUES
('a0000000-0000-0000-0000-000000000001', 'Baby Shark Dance', 'http://example.com/baby-shark.mp4', ARRAY['kids','music','dance'], 'G', 999, now() - interval '1 day'),
('a0000000-0000-0000-0000-000000000002', 'Peppa Pig Muddy Puddles', 'http://example.com/peppa.mp4', ARRAY['kids','cartoon','family'], 'G', 880, now() - interval '2 days'),
('a0000000-0000-0000-0000-000000000003', 'Messi Top 10 Goals', 'http://example.com/messi.mp4', ARRAY['sports','football'], NULL, 1200, now() - interval '3 days'),
('a0000000-0000-0000-0000-000000000004', 'Ronaldo Free Kick Tutorial', 'http://example.com/ronaldo.mp4', ARRAY['sports','training'], NULL, 950, now() - interval '4 days'),
('a0000000-0000-0000-0000-000000000005', 'Gordon Ramsay Perfect Pasta', 'http://example.com/pasta.mp4', ARRAY['cooking','food'], NULL, 820, now() - interval '5 days'),
('a0000000-0000-0000-0000-000000000006', 'ASMR Whisper Eating', 'http://example.com/asmr.mp4', ARRAY['asmr','relax'], '18+', 650, now() - interval '6 days'),
('a0000000-0000-0000-0000-000000000007', '10 Hour Rain Sounds', 'http://example.com/rain.mp4', ARRAY['relax','sleep','ambient'], NULL, 720, now() - interval '7 days'),
('a0000000-0000-0000-0000-000000000008', 'Cat Compilation 2025', 'http://example.com/cats.mp4', ARRAY['cats','funny','animals'], NULL, 1100, now() - interval '8 days'),
('a0000000-0000-0000-0000-000000000009', 'Street Food in Bangkok', 'http://example.com/bangkok.mp4', ARRAY['travel','food'], NULL, 780, now() - interval '9 days'),
('a0000000-0000-0000-0000-000000000010', 'Meditation for Beginners', 'http://example.com/meditation.mp4', ARRAY['wellness','relax'], NULL, 600, now() - interval '10 days'),
('a0000000-0000-0000-0000-000000000011', 'Minecraft Survival Guide', 'http://example.com/minecraft.mp4', ARRAY['gaming','kids'], NULL, 1500, now() - interval '1 hour'),
('a0000000-0000-0000-0000-000000000012', 'NBA Highlights 2025', 'http://example.com/nba.mp4', ARRAY['sports','basketball'], NULL, 890, now() - interval '2 hours'),
('a0000000-0000-0000-0000-000000000013', 'Vegan Breakfast Ideas', 'http://example.com/vegan.mp4', ARRAY['cooking','healthy'], NULL, 540, now() - interval '3 hours'),
('a0000000-0000-0000-0000-000000000014', 'Yoga for Back Pain', 'http://example.com/yoga.mp4', ARRAY['wellness','fitness'], NULL, 670, now() - interval '4 hours'),
('a0000000-0000-0000-0000-000000000015', 'Dog Training Tips', 'http://example.com/dogs.mp4', ARRAY['animals','training'], NULL, 730, now() - interval '5 hours');

-- Generate 2500 realistic user events from 25 users
-- Users have real SHA-256 hashes
WITH users AS (
  SELECT
    '11111111-1111-1111-1111-111111111111'::uuid as tenant_id,
    encode(digest('demo_user_' || generate_series(1,25)::text, 'sha256'), 'hex') as user_hash
)
INSERT INTO user_events (video_id, user_id_hash, event_type, created_at)
SELECT
  v.video_id,
  u.user_hash,
  CASE (random()*5)::int
    WHEN 0 THEN 'watch_completed'
    WHEN 1 THEN 'watch_75'
    WHEN 2 THEN 'watch_50'
    WHEN 3 THEN 'skip'
    WHEN 4 THEN 'like'
    ELSE 'share'
  END,
  now() - (random() * interval '10 days')
FROM users u
CROSS JOIN (SELECT video_id FROM videos ORDER BY random() LIMIT 10) v
CROSS JOIN generate_series(1, 10);  -- ~100 events per user

SQL

echo ""
echo "Test data generated successfully!"
echo ""
echo "Data Summary:"
docker exec -i $DB_CONTAINER psql -U postgres -d storyteller -c "SELECT 'Tenants' as table_name, COUNT(*) as count FROM tenants UNION ALL SELECT 'Videos', COUNT(*) FROM videos UNION ALL SELECT 'User Events', COUNT(*) FROM user_events;"
echo ""
echo "Quick test commands:"
echo "  - Personalized feed (Kids TV user):"
echo "    curl \"http://localhost:8080/v1/feed?tenantId=11111111-1111-1111-1111-111111111111&userIdHash=\$(echo -n 'demo_user_1' | shasum -a 256 | cut -d' ' -f1)&limit=10\""
echo ""
echo "  - Fallback feed (disabled tenant):"
echo "    curl -i \"http://localhost:8080/v1/feed?tenantId=55555555-5555-5555-5555-555555555555&userIdHash=test&limit=10\""
echo ""
echo "  - Post event (with optional metadata):"
echo "    curl -X POST http://localhost:8080/v1/events -H \"Content-Type: application/json\" -d '{\"tenantId\":\"11111111-1111-1111-1111-111111111111\",\"userIdHash\":\"'\$(echo -n 'demo_user_1' | shasum -a 256 | cut -d' ' -f1)'\",\"videoId\":\"a0000000-0000-0000-0000-000000000001\",\"eventType\":\"watch_completed\",\"metadata\":{\"device\":\"iOS\",\"app_version\":\"1.2.3\"}}'"
echo ""
echo "Done! Your prototype now has realistic data to demonstrate personalisation."
