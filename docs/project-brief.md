# Normal Bias: project brief and constraints

Last updated: 2026-11-18.

This document records Robin's requirements and decisions. It is the starting point for people and coding agents working on Normal Bias. Distinguish agreed constraints from proposals; do not silently turn an open question into an architectural decision.

## Purpose and context

Normal Bias is a self-hosted personal music service for an existing MP3 collection of approximately 30 GB, intended as an alternative to iBroadcast. Hosting should be in the cloud rather than on a machine at home.

The longer-term listening targets are mobile, CarPlay and desktop web. Robin has expressed interest in Codename One for the client. Listening-client feasibility and implementation are not the first phase; the PoC companion upload CLI is in scope as described below.

## Agreed constraints

- **Language:** 100% Java.
- **Build:** Maven.
- **Backend:** Spring Boot.
- **Eventual deployment:** deploy4j to a VPS. Deployment is deferred and is not part of the initial PoC.
- **Eventual API hosting:** a running service on the VPS, **not a serverless API**. The initial PoC is tested locally.
- **Runtime topology:** a single service instance.
- **Database:** H2 for the single-instance service; use embedded H2 for local PoC testing.
- **Starting point:** API-first, covering the music catalogue/index, music management, storage, uploads and library management.
- **PoC companion CLI:** a Java CLI uses the service API to upload a directory of files, reports progress and allows cancellation, so the PoC can exercise a real upload scenario.
- **Initial PoC storage:** local file-backed storage for music while testing.
- **Future storage option:** Cloudflare object storage (previously discussed as R2) remains an option for music files. It is not yet a final provider decision and is not required for the local PoC.

The API-first direction replaces the earlier suggestion to begin with a client playback/CarPlay spike. The Spring Boot/VPS decision replaces the earlier serverless API proposal.

## Current status and scope

Robin explicitly authorised implementation of the first local PoC on 2026-11-17, superseding the earlier hold. Scope: MP3 upload, metadata extraction, catalogue list/get, HTTP Range streaming and a cancellable Java directory-upload CLI. Use synthetic fixtures for automated checks; do not upload Robin's personal collection during development.

The implementation uses Java 21 LTS, Maven, Spring Boot 3.5.16, embedded file-backed H2 and local music files. Default API binding is loopback only. Deployment, VPS setup, deploy4j execution, cloud/R2 storage and listening clients remain out of scope.

On 2026-11-18 Robin reported completing the Mac local smoke test and authorised the next API milestone: search, metadata editing and deletion. Search must work effectively across approximately 14,000 songs. All collection queries (get-all/list/search) must be paginated.

This milestone uses a common `{items, offset, limit, total}` collection response (default 50, maximum 200), literal case-insensitive substring search across title/artist/album/filename, partial catalogue metadata edits for title/artist/album, and deletion of the row and stored audio. These are implementation choices within the authorised milestone. Metadata edits do not rewrite MP3 tags or change content IDs. Search is verified with 14,000 synthetic catalogue rows; this is not a real-collection upload or playback test.

Robin subsequently authorised schema migrations, operation recovery and inspection, with backup/restore explicitly deferred to the deployment story. The implementation uses Flyway V1/V2 with checked adoption of the original catalogue and durable H2 upload/deletion intents replayed before HTTP startup. Robin then explicitly requested replacing the offline doctor command with online inspection; that command and its orphan-quarantine option have been removed.

Online inspection runs as a report-only background API job, with quick/full-hash modes, status/progress and paginated findings. One run is active at a time and the five latest runs are retained in memory. Hashing releases the mutation monitor and results are revalidated against concurrent changes; active uploads are excluded from abandoned-staging findings. Startup recovery continues to preserve ambiguous files and may quarantine conflicts; there is no inspection repair endpoint. The API retains exclusive data-directory ownership against another service process. These supported recovery states do not imply hardware/power-loss durability guarantees. See [storage reliability](storage-reliability.md) for the API contract and recovery semantics.

Initial implementation choices (small PoC defaults, not permanent product constraints):

- Raw `audio/mpeg` uploads, 256 MiB maximum per file; SHA-256 exact-file duplicates return the existing track.
- ID3 title/artist/album and frame-derived duration via mp3agic; filename title fallback.
- Paginated catalogue; immutable audio with single byte-range seeking.
- Sequential recursive CLI traversal, no following symbolic links, case-insensitive `.mp3` selection, per-file progress. Cancellation interrupts the request; committed tracks remain. Rerun the directory to retry whole files with deduplication. No byte-level resume.
- Local unauthenticated API for this PoC; CLI accepts literal loopback HTTP origins only. This is not ready for public exposure.

See [local PoC guide](local-poc.md) for the exact contract, commands, verification and remaining limitations.

## Open decisions

Resolve these during planning rather than assuming answers:

- Library organisation beyond search, metadata editing and deletion (for example playlists).
- Future upload/indexing requirements beyond the first PoC defaults above.
- Any future byte-level resumable upload protocol; the initial CLI retries whole files.
- Final object-storage provider and access/streaming approach.
- Authentication and access control.
- Backup/restore requirements and implementation as part of deployment; broader hardware/power-loss recovery beyond the local operation-replay milestone.
- Dependency maintenance policy beyond the pinned initial build versions.
- VPS provider, sizing and deployment configuration.
- Client architecture and Codename One feasibility, including real-device background audio and CarPlay support.

These are planning topics, not a requirement to implement them all in the first PoC.

## Keeping this document current

Record later user-approved constraints and decisions here. Clearly label proposals and unresolved questions. Preserve the distinction between the long-term product and the currently authorised work.
