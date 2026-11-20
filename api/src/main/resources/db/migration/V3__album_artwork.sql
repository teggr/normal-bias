ALTER TABLE tracks ADD COLUMN metadata_revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tracks ADD COLUMN artwork_revision VARCHAR(64) NOT NULL DEFAULT 'embedded';
CREATE TABLE artwork_assets (
  hash VARCHAR(64) PRIMARY KEY,
  image BINARY LARGE OBJECT NOT NULL
);
CREATE TABLE album_selections (
  id VARCHAR(36) PRIMARY KEY,
  provider VARCHAR(64) NOT NULL,
  release_id VARCHAR(36) NOT NULL,
  source_url VARCHAR(2048) NOT NULL,
  accepted_at TIMESTAMP WITH TIME ZONE NOT NULL,
  image_hash VARCHAR(64) NOT NULL REFERENCES artwork_assets(hash),
  evidence CHARACTER LARGE OBJECT NOT NULL
);
CREATE TABLE album_members (
  track_id VARCHAR(64) PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE,
  selection_id VARCHAR(36) NOT NULL REFERENCES album_selections(id)
);
