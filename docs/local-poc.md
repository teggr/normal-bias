# First local PoC

## Build and run

Requires a **Java 21 JDK** (including the compiler) and Maven 3.6.3+. Application and test code are Java; XML/properties/SQL configure the build and database. No listening UI is included.

```sh
mvn clean verify
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar
# Another terminal, from the repository root:
java -jar cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar api/src/test/resources
```

The server binds to `127.0.0.1:8080`. Keep this binding: authentication, TLS and remote access are not implemented. H2's web console is disabled. There is no cloud storage, deployment configuration or network database. Start only one service per data directory. H2's embedded file lock prevents sharing a catalogue across processes.

To use a separate test data directory and loopback port:

```sh
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar --server.port=8081 --normalbias.data-dir=/tmp/normal-bias-demo
java -jar cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar api/src/test/resources http://127.0.0.1:8081
```

The data directory contains `catalogue.mv.db`, `music/<sha256>.mp3` and temporary `music/staging/*.part` files. Files and database are ignored by Git. Preserve both together when moving the collection. Back up only with the service stopped for this PoC; there is no automated backup or migration framework.

## HTTP contract

| Method/path | Behaviour |
| --- | --- |
| `POST /api/tracks?filename=track.mp3` | Raw MP3 body with `Content-Type: audio/mpeg`. New file: 201; identical bytes: 200. JSON track and `Location` header in both cases. Filename defaults to `upload.mp3`. |
| `GET /api/tracks?offset=0&limit=50` | JSON array ordered by creation time then ID; limit 1–200, offset nonnegative. No total-count response. |
| `GET /api/tracks/{id}` | Track JSON, or 404. |
| `GET /api/tracks/{id}/audio` | Exact stored bytes, `audio/mpeg`, `Content-Length`, `Accept-Ranges: bytes`, SHA-256 ETag. |
| `HEAD /api/tracks/{id}/audio` | Same stream headers without a body. |

Track JSON: `id` (lowercase SHA-256 of complete file bytes), `filename` (basename), `title`, nullable `artist`/`album`, `durationMs`, `sizeBytes`, `createdAt` (UTC). ID3v2 is preferred over ID3v1; missing title falls back to filename. Metadata strings are capped at 1024 characters. No artwork extraction or tag editing.

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

Ctrl-C cancels the in-flight HTTP request and prevents further uploads. Already committed files remain. A request whose full body reached the server may still commit even if its response was lost or cancellation happened just afterward. An interrupted partial body is discarded and never catalogued. Rerunning the directory reuploads whole files: exact SHA-256 duplicates return their existing track with original metadata. This is **not byte-level resume**. Different tag bytes make different files even when the audio is the same. Concurrent identical uploads produce one row.

The server stages uploads on disk, validates/parses them, then atomically moves the file and inserts the H2 row. Temporary files are removed on ordinary success/failure and stale `.part` files are cleared at startup. File moves and H2 commits are not one atomic cross-resource transaction: a process/machine crash between them can leave an unlisted orphan MP3. Reuploading that identical file replaces the orphan safely. Automatic reconciliation, fsync durability guarantees and repairs for externally removed stored files are not implemented.

## Verification

`mvn clean verify` runs Java unit tests and live HTTP integration tests using a random loopback port and a temporary file-backed H2 catalogue. Tests use only the bundled synthetic MP3 and generated bytes. FFmpeg is not needed to build or test.

See [verification record](verification.md) for observed results from the task environment, including any checks not run.

## Remaining limitations and future work

- No public exposure: no authentication, authorisation, TLS, quotas or adversarial upload hardening. Loopback defaults do not protect against other local processes.
- No delete/edit/search API, playlists, artwork, transcoding, listening clients or remote uploader.
- No 30 GB load/performance run, real collection validation, device playback testing or crash/power-loss recovery testing.
- mp3agic 0.9.1 is a small pure-Java parser whose upstream is no longer actively maintained. Review parser/dependency maintenance and hardening before expanding beyond trusted local PoC use.
- No schema migration or automatic repair tooling. Missing/truncated stored files fail streaming; do not edit storage behind the service.
- VPS/deploy4j and optional Cloudflare R2 remain future directions, not delivered features.
