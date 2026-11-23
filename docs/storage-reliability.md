# Local storage reliability

This milestone adds versioned schema migrations, restart recovery and online inspection to the single-instance Java/H2 service. Backup and restore are deferred to the deployment story, as Robin requested on 2026-11-18. No deployment, cloud storage or listening client is included.

## Migrations and existing catalogues

The API runs Flyway before storage recovery and before serving HTTP. SQL migrations live in `api/src/main/resources/db/migration/`:

- `V1__catalogue.sql`: the original eight-column tracks schema.
- `V2__pending_operations.sql`: pending upload metadata and pending deletion IDs.
- `V3__album_artwork.sql`: fetched artwork assets/provenance and catalogue revisions.
- `V4__background_artwork.sql`: durable background checks and artwork revision.
- `V5__accounts_sessions.sql`: accounts, hashed credentials/grants, one-use tokens and login throttling. See [authentication](authentication.md).

For a fresh catalogue, Flyway applies all five migrations. For an existing PoC catalogue without Flyway history, startup checks that the PUBLIC schema contains only the expected tracks table, with the original column order/types/lengths/nullability and primary key, and no custom defaults, generated columns or additional constraints. It then records baseline version 1 and applies V2–V5. Existing track IDs, metadata edits, timestamps and audio remain intact. Unrecognised legacy schemas fail startup rather than being silently adopted. This does not validate arbitrary manual schema changes after adoption.

Subsequent starts validate migration history/checksums and apply pending versions. Add new versioned scripts for future changes; do not modify applied migrations. Script initialization is disabled (`spring.sql.init.mode=never`); Flyway is the single schema initializer. Flyway clean is disabled and the application never automatically repairs migration history. Failed migrations/checksum mismatches require investigation, not deleting the database or resetting history.

The default H2 URL uses `WRITE_DELAY=0` so transactions do not intentionally defer flushing writes. Preserve that setting if overriding the JDBC URL. Embedded H2 retains its database lock. A separate `.normal-bias.lock` in the data directory prevents another API process from owning the same files, even when a different JDBC URL is supplied. Startup acquires this lock before migration. Do not delete the lock file while a process is running; the operating system releases ownership when the process exits.

## Recovering operations

The data directory contains:

```text
data/
  .normal-bias.lock
  catalogue.mv.db
  music/
    <sha256>.mp3
    staging/upload-<number>.part
    quarantine/<unique-prefix>-<original-name>
```

Uploads are first streamed into staging, bounded, parsed and hashed as before. Validated staged bytes are flushed before a pending-upload row records the complete catalogue metadata and staging filename. The audio is then atomically moved into its content-addressed location; insertion of the track and removal of the pending row happen in one H2 transaction. An interrupted prepared upload can therefore be completed after restart whether its audio is still staged or has already moved. Recovery verifies file size and SHA-256 before committing metadata. If neither location contains the expected bytes, startup fails with the operation and evidence retained for manual investigation.

Deletion records its pending intent and removes its catalogue row in a single H2 transaction, **before** touching audio. It then removes the audio and finally clears the intent. A database failure leaves both row and audio intact. A file-removal failure leaves the intent for restart; the HTTP request can return 500 even though the row has already been removed. Restart completes committed deletions, including when the audio was already removed. Unexpected non-regular deletion paths or a conflicting catalogue row cause recovery to stop and preserve evidence. This is completion of an authorised deletion, not a trash/undo mechanism.

Recovery runs before HTTP service startup, is safe to repeat, and handles pending deletions before prepared uploads. Re-uploading an ID with a pending deletion finishes that deletion before installing new audio. Installing audio or deleting a track with an unresolved pending upload requires restart/recovery first. Re-uploading an existing track also repairs missing/corrupt audio while retaining its edited catalogue metadata; conflicting audio is moved into quarantine.

Legacy `<sha256>.part` deletion files are no longer blindly removed. If the corresponding track still exists, its destination is absent and its staged bytes match, recovery restores the audio. Other legacy/unknown staging entries are quarantined. Only recognised `upload-<number>.part` fragments with no remaining pending intent are discarded as abandoned request bodies. Legacy orphan MP3s in the music directory are left for inspection rather than automatically catalogued.

Audio, database and directory changes are still not one atomic filesystem/database transaction. Recovery addresses the tested interruption states; it does not guarantee survival of hardware failure, filesystem damage or abrupt power loss. File data is flushed and moves are atomic on the local filesystem, but directory entries are not explicitly fsynced. Normal startup does not hash the entire library; it checks bytes involved in recovery only. Missing or corrupt unrelated audio is reported by online inspection.

## Online inspection

Inspection runs inside the API as a background job. Keep the service running; the upload CLI is unchanged. The former offline `doctor` subcommand and its orphan-quarantine option have been removed.

```sh
# Start a quick check (default), or a full hash check:
curl -i -X POST 'http://127.0.0.1:8080/api/inspections?mode=quick'
curl -i -X POST 'http://127.0.0.1:8080/api/inspections?mode=full'
# Substitute the inspection ID returned by POST, or follow its Location header:
curl http://127.0.0.1:8080/api/inspections/INSPECTION_ID
curl 'http://127.0.0.1:8080/api/inspections/INSPECTION_ID/findings?offset=0&limit=50'
```

Only one inspection runs at a time. POST returns **202**, a status object and a `Location` header pointing to that run. Another start while it is running returns **409**. Invalid modes return **400**. Status/findings for an unknown or evicted UUID return **404**; malformed UUIDs return **400**. HTTP examples above assume explicit loopback demo mode. Required mode permits only the owner to inspect over HTTPS; the entry point advertises starting an inspection only when idle, otherwise its running status link. See [authentication](authentication.md).

| Method/path | Response |
| --- | --- |
| `POST /api/inspections?mode=quick` | Start a background quick/full scan; 202 with initial status and Location. |
| `GET /api/inspections/{id}` | One run's state, progress, aggregate counts, timestamps and any failure. |
| `GET /api/inspections/{id}/findings?offset=0&limit=50` | `{items, offset, limit, total, state}`; limit 1–200, offset nonnegative. |

The run moves from `RUNNING` to `COMPLETED` or `FAILED`. Status contains `id`, `mode`, `state`, `startedAt`, nullable `completedAt`, `snapshotTracks`, `scannedTracks`, `skippedChangedTracks`, `total`, `counts` and nullable `error`. `scannedTracks` counts visited snapshot entries, including changed entries skipped during checking. `total` counts findings, not songs, and `counts` groups findings by kind. A failed scan retains its partial findings and explains the error; it is not a clean result even if its total is zero.

Findings are appended while the job runs. Each page is a consistent copy; its total/state can change until the scan finishes. Once completed, pages remain stable and fetching another page never restarts the scan or rehashes the library. An offset beyond the current end returns empty `items` with the total retained. The five most recently started runs and their findings are kept **in memory**; older IDs are evicted, and restart clears all run history. Shutdown interrupts the worker. Inspection does not create database tables or change migration history.

Quick mode examines catalogue IDs, file existence/type/size, orphan MP3s, pending operations, leftover staging and quarantined paths. It does not follow symlinks. Full mode also calculates SHA-256 for regular catalogue files whose size matches; a size mismatch is already a finding. Audio is streamed in 64 KiB buffers, so a full check does not load files into memory. Full checking may read the whole 30 GB collection. A run retains a catalogue snapshot, directory-entry lists and all findings for subsequent paginated retrieval, suitable for the current approximately 14,000-track scope; no large-library memory or real-collection throughput guarantee is claimed.

### Inspection alongside uploads and deletions

A scan starts with a short catalogue snapshot. It checks each row and opens its audio under the same monitor used by catalogue mutations, then releases that monitor before hashing. The API can list, upload, edit and delete while the hash read is in progress. Before reporting the hash result, the scan rechecks catalogue metadata, the track's in-process audio mutation version and file identity/size/modification time. Deletion, replacement or editing during the check increments `skippedChangedTracks` rather than producing a stale corruption report. Re-run inspection to cover skipped entries or songs added after the initial snapshot.

Orphan candidates are rechecked against the live catalogue and pending operation records under that monitor. Currently active upload staging names are tracked and excluded; pending uploads/deletions are reported only once they are observable outside normal committed operations. No long global lock is held for the full scan. Findings are observations during the run, not a transactionally frozen view of the library: a reported problem can subsequently be fixed by a re-upload. Modifying stored files outside the API while it is running is unsupported.

Kinds include `MISSING`, `SIZE_MISMATCH`, `HASH_MISMATCH`, `INVALID_ID`, `UNEXPECTED_FILE`, `UNREADABLE`, `ORPHAN`, `PENDING_UPLOAD`, `PENDING_DELETION`, `STAGING_FILE` and `QUARANTINED`. Findings record a relative path, optional track ID and explanation. A directory/database traversal failure marks the run `FAILED` rather than silently omitting unchecked storage.

Inspection is report-only: it never moves, deletes, imports or rewrites music or catalogue metadata. There is no online quarantine/repair endpoint in this change. Existing startup recovery may still preserve conflicting/ambiguous files in quarantine; re-uploading the same MP3 still repairs missing/corrupt audio while retaining catalogue edits. Backup/restore remains deferred to deployment. If recovery prevents the API from starting, inspect the startup error and preserved files manually; no offline inspection executable remains.

## Verification

Run `mvn clean verify` using Java 21. Tests use temporary file-backed H2 and synthetic audio only. They cover fresh/legacy migrations, checksum rejection, durable operation replay, preservation on failure and duplicate repair. Online checks cover quick/full detection, HTTP run lifecycle/failure/retention, paginated findings across 14,000 rows, active-upload exclusion and deterministic concurrent hashing with deletion/re-upload or audio repair. See [verification.md](verification.md) for actual results and remaining gaps.

## Album artwork storage (V3) — 2026-11-20

V3 adds track metadata/artwork revisions and separate `artwork_assets`, `album_selections`, `album_members` tables. Accepted image bytes are a durable recovery source in H2; the API atomically materializes normalized PNG files under the separate `artwork/` directory before committing associations/revisions/provenance in one transaction. Startup reconstructs missing or corrupt artwork files before serving requests. A failed or stale acceptance preserves the last committed associations; unfinished acceptance is not silently replayed as approval. Lookup jobs expire and must be restarted. Audio upload/deletion intents retain their existing recovery semantics; no offline inspection/repair command is added.

Deleting a track cascades its artwork association but never deletes a shared asset or selection provenance. Unreferenced assets and selection evidence remain retained until a later explicit cleanup policy. Online audio inspection still scans `music/`; it does not validate artwork sidecars. Backup/restore remains deferred. See [album artwork](album-artwork.md) for the API, recovery limits and original-byte guarantees.


## Background artwork work — 2026-11-20

Flyway V4 adds `artwork_checks` and `artwork_version` without changing music/storage-operation records or applied migration scripts. Per-track metadata revisions, attempt counts, states and retry times persist across restarts; deleting a track cascades its check. A 15-minute `CHECKING` lease recovers unfinished work after process failure. The worker performs bounded metadata-only matching separately from uploads, catalogue requests, streaming and inspection. Matching results use the V3 recoverable asset/provenance publication path and revalidate catalogue/artwork revisions before committing. The global cover revision increments in the same transaction as committed associations. No source audio/tags are changed. See the [artwork guide](album-artwork.md) for retry/selection policy and opt-out configuration.
