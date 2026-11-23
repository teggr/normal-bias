CREATE TABLE auth_guard (id INT PRIMARY KEY CHECK (id = 1));
INSERT INTO auth_guard VALUES (1);

CREATE TABLE auth_accounts (
  id VARCHAR(36) PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(512) NOT NULL,
  role VARCHAR(16) NOT NULL CHECK (role IN ('OWNER', 'LISTENER')),
  enabled BOOLEAN NOT NULL,
  created_at BIGINT NOT NULL
);
CREATE TABLE auth_credentials (
  id VARCHAR(36) NOT NULL UNIQUE,
  token_hash VARCHAR(64) PRIMARY KEY,
  account_id VARCHAR(36) NOT NULL REFERENCES auth_accounts(id),
  kind VARCHAR(16) NOT NULL CHECK (kind IN ('COOKIE', 'BEARER', 'UPLOAD')),
  created_at BIGINT NOT NULL,
  last_seen BIGINT NOT NULL,
  idle_expires BIGINT NOT NULL,
  absolute_expires BIGINT NOT NULL,
  revoked BOOLEAN NOT NULL
);
CREATE INDEX auth_credentials_account ON auth_credentials(account_id);
CREATE TABLE auth_playback_grants (
  token_hash VARCHAR(64) PRIMARY KEY,
  credential_id VARCHAR(36) NOT NULL REFERENCES auth_credentials(id) ON DELETE CASCADE,
  track_id VARCHAR(64) NOT NULL REFERENCES tracks(id) ON DELETE CASCADE,
  expires_at BIGINT NOT NULL
);
CREATE INDEX auth_playback_expiry ON auth_playback_grants(expires_at);
CREATE TABLE auth_one_time_tokens (
  id VARCHAR(36) NOT NULL UNIQUE,
  token_hash VARCHAR(64) PRIMARY KEY,
  kind VARCHAR(16) NOT NULL CHECK (kind IN ('INVITE', 'RESET')),
  account_id VARCHAR(36) REFERENCES auth_accounts(id),
  created_at BIGINT NOT NULL,
  expires_at BIGINT NOT NULL,
  consumed BOOLEAN NOT NULL
);
CREATE INDEX auth_one_time_account ON auth_one_time_tokens(account_id);
CREATE TABLE auth_login_limits (
  key_hash VARCHAR(64) PRIMARY KEY,
  window_start BIGINT NOT NULL,
  failures INT NOT NULL
);
