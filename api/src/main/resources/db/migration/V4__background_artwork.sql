CREATE TABLE artwork_checks (
  track_id VARCHAR(64) PRIMARY KEY REFERENCES tracks(id) ON DELETE CASCADE,
  metadata_revision BIGINT NOT NULL,
  state VARCHAR(32) NOT NULL,
  attempts INT NOT NULL,
  next_attempt TIMESTAMP WITH TIME ZONE
);
CREATE INDEX artwork_checks_due ON artwork_checks(next_attempt);
CREATE TABLE artwork_version (
  id INT PRIMARY KEY CHECK (id = 1),
  revision BIGINT NOT NULL
);
INSERT INTO artwork_version VALUES (1, 0);
