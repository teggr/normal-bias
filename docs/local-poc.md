# First local PoC

## Build and run

Requires a **Java 21 JDK** (including the compiler) and Maven 3.6.3+. Application and test code are Java; XML/properties/SQL configure the build and database. The optional [web listening client](client-poc.md) is built separately and can be served by this API.

```sh
mvn clean verify
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar
# Another terminal, from the repository root:
java -jar cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar api/src/test/resources
```

The server binds to `127.0.0.1:8080`. Keep this binding: authentication, TLS and remote access are not implemented. H2's web console is disabled. There is no cloud storage, deployment configuration or network database. Start only one service per data directory. An exclusive data-directory lock prevents a second API process; H2 also locks its embedded catalogue.

To use a separate test data directory and loopback port:

```sh
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar --server.port=8081 --normalbias.data-dir=/tmp/normal-bias-demo
java -jar cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar api/src/test/resources http://127.0.0.1:8081
```

The data directory contains `catalogue.mv.db`, `.normal-bias.lock`, `music/<sha256>.mp3`, temporary `music/staging/*.part` files and preserved `music/quarantine/` entries. Files and database are ignored by Git. Preserve the database and music together when moving the collection, with the service stopped. Startup runs Flyway migrations and replays pending operations before serving HTTP. Backup/restore is deferred to deployment. See [storage reliability](storage-reliability.md) for upgrade checks, recovery and background online inspection.

## HTTP contract

| Method/path | Behaviour |
| --- | --- |
| `POST /api/tracks?filename=track.mp3` | Raw MP3 body with `Content-Type: audio/mpeg`. New file: 201; identical bytes: 200. JSON track and `Location` header in both cases. Filename defaults to `upload.mp3`. |
| `GET /api/tracks?q=&offset=0&limit=50` | Paginated list/search: `{items, offset, limit, total}`, ordered by creation time then ID; limit 1–200, offset nonnegative. |
| `GET /api/tracks/{id}` | Track JSON, or 404. |
| `PATCH /api/tracks/{id}` | JSON partial edit of title/artist/album; returns updated track, or 404. |
| `DELETE /api/tracks/{id}` | Removes catalogue entry and stored audio; 204 with no body, or 404 (including repeat deletion). |
| `GET /api/tracks/{id}/audio` | Exact stored bytes, `audio/mpeg`, `Content-Length`, `Accept-Ranges: bytes`, SHA-256 ETag. |
| `HEAD /api/tracks/{id}/audio` | Same stream headers without a body. |
| `POST /api/inspections?mode=quick` | Starts a background quick/full check; 202 with run status and Location; 409 if busy. |
| `GET /api/inspections/{id}` | Run state/progress/counts/error; 404 for unknown/evicted runs. |
| `GET /api/inspections/{id}/findings?offset=0&limit=50` | Paginated findings and run state; default 50, maximum 200. |

Track JSON: `id` (lowercase SHA-256 of complete file bytes), `filename` (basename), `title`, nullable `artist`/`album`, `durationMs`, `sizeBytes`, `createdAt` (UTC). ID3v2 is preferred over ID3v1; missing title falls back to filename. Metadata strings are capped at 1024 characters. No artwork extraction or tag editing.

### Listing, searching and managing tracks

All collection queries use the same page envelope; there is no unbounded get-all operation. Omitting `q`, or providing whitespace, lists all tracks within the page. `total` counts all matching rows before pagination. An offset beyond the end returns an empty `items` array and retains the matching total. This replaces the initial PoC's bare-array list response. Single-track get/upload/edit responses remain track objects. The upload CLI does not consume collection responses.

Search trims `q` (maximum 1024 characters) and matches a case-insensitive literal substring in any of title, artist, album or filename. `%`, `_` and quotes are literal characters. The whole query must match within a field; there is no token splitting, fuzzy matching, relevance ranking or accent folding. Results retain creation-time/ID order. Each page and its total are consistent within the single service instance; separate page requests are not a snapshot if tracks are added, edited or deleted between requests.

H2 evaluates substring filters over catalogue metadata; audio files are never read for search. A dedicated full-text index is not required by this implementation. The automated scale check uses 14,000 synthetic catalogue entries, exercises common/rare/absent terms and pagination, and prints observed HTTP latency without imposing a hardware-dependent timing assertion. See the verification record for measured results and limits.

Metadata PATCH requires a nonempty JSON object containing only `title`, `artist` and/or `album`. Omitted fields retain their values. Values must be strings or null; strings are trimmed, limited to 1024 characters and reject embedded control characters. Title cannot be blank or null. Null or blank artist/album clears that field. Invalid edits return 400 without changing any fields. Unknown or read-only fields are rejected. Malformed JSON returns 400; unsupported content types return 415.

Edits affect H2 catalogue metadata only: stored audio, embedded tags, filename, hash ID, duration, size and creation time stay unchanged. Duplicate uploads return the existing edited metadata. Deleting a track removes its audio as well; re-uploading the same MP3 creates it anew using embedded tags. A missing audio file does not prevent deleting its stale row. Upload commits, edits, deletes and page queries share a single-instance lock so writes cannot interleave within those operations. Audio streaming is not locked; a concurrent deletion may prevent a stream from opening.

```sh
curl 'http://127.0.0.1:8080/api/tracks?q=synthetic&offset=0&limit=50'
# Substitute a returned ID. PATCH changes the catalogue; DELETE permanently removes stored audio.
curl -X PATCH -H 'Content-Type: application/json' --data '{"title":"Updated title","artist":null}' http://127.0.0.1:8080/api/tracks/ID
curl -X DELETE http://127.0.0.1:8080/api/tracks/ID
```

```sh
curl -i -H 'Content-Type: audio/mpeg' --data-binary @api/src/test/resources/tone.mp3 'http://127.0.0.1:8080/api/tracks?filename=tone.mp3'
curl 'http://127.0.0.1:8080/api/tracks?limit=10'
# Substitute the returned ID:
curl -i -H 'Range: bytes=0-99' http://127.0.0.1:8080/api/tracks/ID/audio
curl -I http://127.0.0.1:8080/api/tracks/ID/audio
```

Single byte ranges support `bytes=start-end`, `bytes=start-` and `bytes=-suffixLength`. Successful ranges return 206 with `Content-Range`; ends are clamped to file length. Invalid, unsatisfiable and multiple ranges return 416 and `Content-Range: bytes */size`. Multiple-range multipart responses are deliberately unsupported. A matching strong ETag in `If-Range` permits seeking; unmatched validators (including dates) return the full file. Conditional 304 responses are not implemented.

Empty uploads return 400; unsupported content types 415; data without valid MP3 frames 422; oversized bodies 413. The default upload cap is 268435456 bytes (256 MiB), configurable with `--normalbias.max-upload-bytes=...`. Both known-length and chunked bodies are bounded. Parser acceptance is not a full decode/integrity check of every audio frame.

## Upload and cancellation semantics

The CLI walks the directory recursively in filesystem traversal order, sequentially uploads regular files with case-insensitive `.mp3` extensions, skips other files and all symlinks, and rejects a symlink root. It does not load the whole collection or whole audio files into memory. Keep the source directory unchanged while uploading; source snapshots and races with local filesystem changes are outside this PoC.

Progress shows the filename, bytes handed to the HTTP client in roughly 10% steps, then server success/failure, then counts. Bytes handed to the client are not an acknowledgement of durable storage. Errors are reported per file and traversal continues. Exit codes: 0 success, 1 one or more file failures, 2 usage/setup failure, 130 programmatic cancellation (Ctrl-C typically also exits 130 on Unix).

Ctrl-C cancels the in-flight HTTP request and prevents further uploads. Already committed files remain. A request whose full body reached the server may still commit even if its response was lost or cancellation happened just afterward. An interrupted partial body is discarded and never catalogued. Rerunning the directory reuploads whole files: exact SHA-256 duplicates return their existing track with current catalogue metadata, including edits. This is **not byte-level resume**. Different tag bytes make different files even when the audio is the same. Concurrent identical uploads produce one row.

The server stages uploads, validates/parses them, flushes validated bytes and records a durable pending-upload row before moving audio into place. Track insertion and removal of the pending intent commit together. Startup can complete interrupted prepared uploads, checking their size and SHA-256. An incomplete request body without an intent is discarded; ambiguous files are preserved or quarantined. Re-uploading an existing track repairs missing/corrupt audio without overwriting edited catalogue metadata.

Deletion commits removal of the track row and a deletion intent together before removing audio. If the database transaction fails, audio is untouched. If file removal fails, the intent remains for startup recovery; the request can return 500 even though the row is gone. Startup finishes known deletions and restores recoverable legacy deletion staging instead of blindly discarding it. There is no trash/undo.

These are restart-recovery protocols, not an atomic database/filesystem transaction or a power-loss guarantee. Recovery stops and retains evidence when bytes or operation records conflict. Use [online inspection](storage-reliability.md#online-inspection) to check missing, corrupt, orphaned and pending files while the API runs, with paginated findings and optional background hashing. Inspection makes no catalogue/music changes and excludes active uploads. Hash results are revalidated after concurrent mutations.

## Verification

`mvn clean verify` runs Java unit tests and live HTTP integration tests using a random loopback port and a temporary file-backed H2 catalogue. Tests use only the bundled synthetic MP3 and generated bytes. FFmpeg is not needed to build or test.

See [verification record](verification.md) for observed results from the task environment, including any checks not run.

## Remaining limitations and future work

- No public exposure: no authentication, authorisation, TLS, quotas or adversarial upload hardening. Loopback defaults do not protect against other local processes.
- No playlists, artwork, embedded-tag rewriting, transcoding, native mobile listening client or remote uploader.
- No 30 GB load/performance run, real collection validation, device playback testing or abrupt power-loss testing. Recovery is tested through deterministic interrupted-operation states and restarts.
- mp3agic 0.9.1 is a small pure-Java parser whose upstream is no longer actively maintained. Review parser/dependency maintenance and hardening before expanding beyond trusted local PoC use.
- No general automatic repair/import of ambiguous files or missing source material. Missing/truncated stored files fail streaming until repaired; do not edit storage behind the running service.
- VPS/deploy4j and optional Cloudflare R2 remain future directions, not delivered features.
