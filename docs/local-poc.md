# First local PoC

## Build and run

The API requires HTTPS/authentication by default; see [authentication](authentication.md). The commands and unauthenticated curl examples below explicitly use **loopback demo mode** for the current client. Required mode instead uses credentials, advertised action links and scoped audio URLs.

Requires a **Java 21 JDK** (including the compiler) and Maven 3.6.3+. Application and test code are Java; XML/properties/SQL configure the build and database. The optional [web listening client](client-poc.md) is built separately and can be served by this API.

```sh
mvn clean verify
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar --normalbias.auth.mode=demo
# Another terminal, from the repository root:
java -jar cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar api/src/test/resources
```

The server binds to `127.0.0.1:8080`. Keep this binding for demo mode; startup rejects non-loopback demo binds. Required mode has server accounts/sessions and enforces HTTPS (plain API requests return 426); client login integration and public deployment are not delivered. H2's web console is disabled. There is no cloud storage, deployment configuration or network database. Start only one service per data directory. An exclusive data-directory lock prevents a second API process; H2 also locks its embedded catalogue.

To use a separate test data directory and loopback port:

```sh
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar --normalbias.auth.mode=demo --server.port=8081 --normalbias.data-dir=/tmp/normal-bias-demo
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
| `GET/HEAD /api/tracks/{id}/artwork` | Embedded cover first, then fetched cover as `image/png`; otherwise 404. HEAD omits body. Private `no-cache`, image-content ETag and conditional 304; missing covers use `no-store`. |
| `POST /api/inspections?mode=quick` | Starts a background quick/full check; 202 with run status and Location; 409 if busy. |
| `GET /api/inspections/{id}` | Run state/progress/counts/error; 404 for unknown/evicted runs. |
| `GET /api/inspections/{id}/findings?offset=0&limit=50` | Paginated findings and run state; default 50, maximum 200. |

Track JSON: `id` (lowercase SHA-256 of complete file bytes), `filename` (basename), `title`, nullable `artist`/`album`, `durationMs`, `sizeBytes`, `createdAt` (UTC). ID3v2 is preferred over ID3v1; missing title falls back to filename. Metadata strings are capped at 1024 characters. Artwork is served separately; no image bytes are added to catalogue JSON. Required authentication mode additionally advertises `_links` for permitted actions/navigation, preserves pagination fields, and applies `no-store` response caching. Its audio URLs require an issued grant. No embedded-tag rewriting.

### Embedded covers

Artwork is read on demand from the existing immutable MP3's leading ID3v2 tag, including files imported before this endpoint existed. No reimport, migration, external cover lookup or durable sidecar files are needed. The server reads at most a 16 MiB tag, accepts at most 8 MiB of JPEG/PNG cover data, and checks dimensions before decoding (maximum 4096 per dimension and 16 million pixels). Missing, malformed, unsupported or excessive art returns 404 so clients can use a fallback. A storage I/O failure remains a server failure rather than an invented cover.

Images are resized proportionally to at most 512 pixels on their longest edge, encoded as PNG without source metadata, and cached in a 16-entry in-memory LRU, including missing-art results. Extraction is serialized to bound simultaneous decoding memory and does not hold the upload/deletion monitor. Track existence is checked before serving cached covers, including after deletion. Deleting audio removes its embedded artwork with it. Catalogue edits and exact-file duplicates do not change the cover or original audio. Cache contents disappear on restart and regenerate from the stored MP3.

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

Progress shows the filename, bytes handed to the HTTP client in roughly 10% steps, then server success/failure, then counts. Bytes handed to the client are not an acknowledgement of durable storage. Ordinary errors are reported per file and traversal continues; 401/403 stop traversal immediately. Exit codes: 0 success, 1 file/access failure, 2 usage/setup failure, 130 programmatic cancellation (Ctrl-C typically also exits 130 on Unix). Authenticated use adds `--token-file=PATH`, requires trusted loopback HTTPS, follows the advertised upload action and refuses cross-origin links/redirects. HTTP remains for demo use only; remote origins remain excluded. See [credential-file setup](authentication.md#authenticated-uploader).

Ctrl-C cancels the in-flight HTTP request and prevents further uploads. Already committed files remain. A request whose full body reached the server may still commit even if its response was lost or cancellation happened just afterward. An interrupted partial body is discarded and never catalogued. Rerunning the directory reuploads whole files: exact SHA-256 duplicates return their existing track with current catalogue metadata, including edits. This is **not byte-level resume**. Different tag bytes make different files even when the audio is the same. Concurrent identical uploads produce one row.

The server stages uploads, validates/parses them, flushes validated bytes and records a durable pending-upload row before moving audio into place. Track insertion and removal of the pending intent commit together. Startup can complete interrupted prepared uploads, checking their size and SHA-256. An incomplete request body without an intent is discarded; ambiguous files are preserved or quarantined. Re-uploading an existing track repairs missing/corrupt audio without overwriting edited catalogue metadata.

Deletion commits removal of the track row and a deletion intent together before removing audio. If the database transaction fails, audio is untouched. If file removal fails, the intent remains for startup recovery; the request can return 500 even though the row is gone. Startup finishes known deletions and restores recoverable legacy deletion staging instead of blindly discarding it. There is no trash/undo.

These are restart-recovery protocols, not an atomic database/filesystem transaction or a power-loss guarantee. Recovery stops and retains evidence when bytes or operation records conflict. Use [online inspection](storage-reliability.md#online-inspection) to check missing, corrupt, orphaned and pending files while the API runs, with paginated findings and optional background hashing. Inspection makes no catalogue/music changes and excludes active uploads. Hash results are revalidated after concurrent mutations.

## Verification

`mvn clean verify` runs Java unit tests and live HTTP integration tests using a random loopback port and a temporary file-backed H2 catalogue. Tests use only the bundled synthetic MP3 and generated bytes. FFmpeg is not needed to build or test.

See [verification record](verification.md) for observed results from the task environment, including any checks not run.

## Remaining limitations and future work

- No public deployment: server authentication/authorisation and direct TLS are implemented, but client login integration, general quotas and adversarial upload hardening remain. Demo mode is explicitly unauthenticated; loopback defaults do not protect against other local processes.
- No playlists, embedded-tag rewriting, transcoding or remote uploader. The optional local iPhone client has a separate trial guide and verification scope.
- No 30 GB load/performance run, real collection validation, device playback testing or abrupt power-loss testing. Recovery is tested through deterministic interrupted-operation states and restarts.
- mp3agic 0.9.1 is a small pure-Java parser whose upstream is no longer actively maintained. Review parser/dependency maintenance and hardening before expanding beyond trusted local PoC use.
- No general automatic repair/import of ambiguous files or missing source material. Missing/truncated stored files fail streaming until repaired; do not edit storage behind the running service.
- VPS/deploy4j and optional Cloudflare R2 remain future directions, not delivered features.

## Accepted album covers — 2026-11-20

The service automatically looks up missing artwork in the background, including the existing catalogue and later uploads. It preserves embedded/existing fetched covers and original audio/tags. Flyway V3 stores recoverable cover assets/provenance; V4 adds durable checks/retries and the global revision used for passive client refresh. The earlier explicit match/preview/accept API remains compatible but is not exposed by the listening client. Track JSON retains `artworkRevision`; Range streaming and metadata edit contracts are unchanged. See [album-artwork.md](album-artwork.md) for automatic selection, bounded batches, delayed retries, opt-out configuration and API routes.

## Library views (2026-11-20)

`GET /api/library/albums` and `GET /api/library/artists` return `{items, offset, limit, total}` with the existing pagination bounds (default 50, maximum 200). Each item contains `{artist, album, trackCount, trackId}`: `trackId` is a deterministic representative (minimum track ID) for the existing artwork endpoint. Artist rows have an empty album value. Totals count groups, and trackCount counts songs in each group. Empty catalogues return empty pages.

Albums group by exact catalogue artist and album values; artists group by exact artist. Null and empty metadata share the empty-string key, displayed as Unknown in the client. Case variants remain distinct. Results sort case-insensitively by artist then album with exact-text ties. These are live projections: edits/uploads/deletions appear on the next request without a migration or reimport. Grouping uses track artist; there is no separate album-artist/disc/track-number model yet.

Both endpoints accept literal case-insensitive substring `q` (up to 1024 characters): artist name for Artists, artist or album name for Albums. Optional `artist` filters groups by exact value. Existing `GET /api/tracks` additionally accepts exact `artist` and `album` filters combined with `q`; omitted filters preserve existing behaviour, while an explicitly empty value selects missing metadata. Track ordering remains creation time then ID. Names are query parameters and must be URL encoded, including ampersands, percent signs and non-ASCII text.

```sh
curl 'http://127.0.0.1:8080/api/library/artists?limit=25'
curl 'http://127.0.0.1:8080/api/library/albums?artist=Example%20Artist'
curl 'http://127.0.0.1:8080/api/tracks?artist=Example%20Artist&album=Example%20Album'
```

## Alphabetical browsing and index links (2026-11-24)

`GET /api/tracks`, `/api/library/albums` and `/api/library/artists` accept optional `sort=alphabetical`. Without it, their existing ordering remains unchanged. Alphabetical songs sort by title; albums by album name then artist; artists by artist name, with deterministic raw-name/identity ties. Exact case-sensitive group identity is preserved. Comparison strips surrounding whitespace, uses Unicode NFKD decomposition, removes combining marks and lowercases with Locale.ROOT. Names beginning a–z follow A–Z; missing names, numbers, symbols and other initials follow in #. Articles are retained. Flyway V6 introduces generated, indexed sort keys; upload and metadata edits update them automatically without rewriting MP3s. Applied migration scripts remain unchanged.

Discovery (`GET /api`) advertises `songs-alphabetical`, `albums-alphabetical` and `artists-alphabetical` only to callers allowed to listen (and literal-loopback demo mode). Alphabetical collections retain `{items, offset, limit, total}`, add row `initial` and advertise `_links.index` alongside self/next/previous. Group contents links preserve the requested batch limit; artist contents request alphabetical albums, while album contents retain catalogue track order. Browsing links are returned in demo mode too; authenticated item action links remain scoped as before. Existing clients may ignore these additive fields.

Follow the advertised index relation to `GET /api/library/index?view=songs|albums|artists`, with the same `q`, `artist`, optional `album`, and `limit` (1–200, default 25). It returns `{items, total}` with exactly 27 sections A–Z / #. A section has `{initial, count, offset, _links}`. Its GET `jump` link points to the first row of that section, or the next populated section, falling back to the final populated section. `count` is the requested section's count; `offset` and `jump` identify the resolved destination. Empty collections omit jump actions. Filtering/search semantics match the corresponding collection exactly. For example, obtain `/api/tracks?sort=alphabetical&limit=25`, follow `_links.index.href`, then follow the M section's `_links.jump.href` to jump directly without fetching preceding batches.

Alphabet indexes and batches are live queries, not persistent snapshots or permissions grants. Clients refresh the index after jumps and revalidate generation before adopting responses; catalogue mutations can change offsets between requests. Every request still enforces the caller's current listening permission. Invalid view/sort/query lengths or pagination bounds return 400. Default API pagination (50, maximum 200), track identity, immutable audio, authentication and session-local queue remain unchanged.
