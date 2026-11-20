# Normal Bias: project brief and constraints

Last updated: 2026-11-20.

This document records Robin's requirements and decisions. It is the starting point for people and coding agents working on Normal Bias. Distinguish agreed constraints from proposals; do not silently turn an open question into an architectural decision.

## Purpose and context

Normal Bias is a self-hosted personal music service for an existing MP3 collection of approximately 30 GB, intended as an alternative to iBroadcast. Hosting should be in the cloud rather than on a machine at home.

The listening targets are desktop web, mobile and eventually CarPlay. Following the API-first milestones, Robin authorised a Codename One listening-client PoC on 2026-11-19, starting with the web/JavaScript platform before mobile. Application source remains Java with Maven; Codename One generates the browser JavaScript, with the browser audio bridge described below.

## Agreed constraints

- **Language:** 100% Java is the agreed application-language goal. The current PoCs have documented platform-glue exceptions: narrow native JavaScript audio glue works around a failure in Codename One's built-in browser media adapter, and a small Objective-C iOS MediaPlayer bridge supplies system commands/metadata absent from the pinned port. UI, API access and queue policy remain Java; these are bounded integration exceptions rather than a decision to replace the client stack.
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

The API-first direction replaced the earlier suggestion to begin with a client playback/CarPlay spike; the completed API milestones now support the authorised web client increment. The Spring Boot/VPS decision replaces the earlier serverless API proposal.

## Current status and scope

Robin explicitly authorised implementation of the first local PoC on 2026-11-17, superseding the earlier hold. Scope: MP3 upload, metadata extraction, catalogue list/get, HTTP Range streaming and a cancellable Java directory-upload CLI. Use synthetic fixtures for automated checks; do not upload Robin's personal collection during development except for the two albums explicitly authorised below.

The implementation uses Java 21 LTS, Maven, Spring Boot 3.5.16, embedded file-backed H2 and local music files. Default API binding is loopback only. Deployment, VPS setup, deploy4j execution and cloud/R2 storage remain out of scope. Listening-client work is now authorised as described below.

On 2026-11-18 Robin reported completing the Mac local smoke test and authorised the next API milestone: search, metadata editing and deletion. Search must work effectively across approximately 14,000 songs. All collection queries (get-all/list/search) must be paginated.

This milestone uses a common `{items, offset, limit, total}` collection response (default 50, maximum 200), literal case-insensitive substring search across title/artist/album/filename, partial catalogue metadata edits for title/artist/album, and deletion of the row and stored audio. These are implementation choices within the authorised milestone. Metadata edits do not rewrite MP3 tags or change content IDs. Search is verified with 14,000 synthetic catalogue rows; this is not a real-collection upload or playback test.

Robin subsequently authorised schema migrations, operation recovery and inspection, with backup/restore explicitly deferred to the deployment story. The implementation uses Flyway V1/V2 with checked adoption of the original catalogue and durable H2 upload/deletion intents replayed before HTTP startup. Robin then explicitly requested replacing the offline doctor command with online inspection; that command and its orphan-quarantine option have been removed.

Online inspection runs as a report-only background API job, with quick/full-hash modes, status/progress and paginated findings. One run is active at a time and the five latest runs are retained in memory. Hashing releases the mutation monitor and results are revalidated against concurrent changes; active uploads are excluded from abandoned-staging findings. Startup recovery continues to preserve ambiguous files and may quarantine conflicts; there is no inspection repair endpoint. The API retains exclusive data-directory ownership against another service process. These supported recovery states do not imply hardware/power-loss durability guarantees. See [storage reliability](storage-reliability.md) for the API contract and recovery semantics.

### Listening-client PoC authorised 2026-11-19

Use Codename One and the existing Normal Bias API. Start on the web/JavaScript platform so the generated client can be built and exercised in this environment before moving to Android/iOS. API changes needed to support the client are authorised. Keep the API's local-only defaults and existing storage/recovery behaviour.

The first increment covers paginated browsing/search, track streaming, play/pause, seeking and an in-memory queue with previous/next and completion advance. Empty, loading and failure states must be visible. The queue is session-local; persistent server playlists, authentication, remote deployment and offline downloads are deferred. Use synthetic audio for verification.

The first web increment is implemented in a separate `client/` Maven project using Codename One 7.0.282. The generated static bundle is served by the existing API using Spring Boot's static-resource launch option; no API contract changes were required. Java owns the UI, API access and finite session queue. A small browser-native JavaScript audio bridge works around a verified failure in the framework's built-in media wrapper. This is platform glue, not a replacement web framework. See [client PoC](client-poc.md) for commands, behaviour and the integration limitation.

Native background playback, lock-screen/headphone commands, title/artist/artwork and call/headphone/network interruption handling remain feasibility goals for physical Android and iOS testing after the browser increment. Browser success does not establish native media or CarPlay support. Artwork was outside that first increment; the subsequent 2026-11-20 artwork milestone is authorised below. Codename One's free plan is the starting point; current pricing and build limits must be checked before cloud builds.

Initial implementation choices (small PoC defaults, not permanent product constraints):

- Raw `audio/mpeg` uploads, 256 MiB maximum per file; SHA-256 exact-file duplicates return the existing track.
- ID3 title/artist/album and frame-derived duration via mp3agic; filename title fallback.
- Paginated catalogue; immutable audio with single byte-range seeking.
- Sequential recursive CLI traversal, no following symbolic links, case-insensitive `.mp3` selection, per-file progress. Cancellation interrupts the request; committed tracks remain. Rerun the directory to retry whole files with deduplication. No byte-level resume.
- Local unauthenticated API for this PoC; CLI accepts literal loopback HTTP origins only. This is not ready for public exposure.

See [local PoC guide](local-poc.md) for the exact contract, commands, verification and remaining limitations.

## Cloud UI evidence requirement

On 2026-11-19 Robin requested a repository skill requiring similar demonstration recordings for cloud UI feature work and bug fixes where appropriate. The [UI recording skill](../.agents/skills/ui-demo-recording/SKILL.md), required by `AGENTS.md`, makes capture and verification part of completing those tasks. Audio-related changes must include actual client sound. Every cloud task records either a verified delivered recording, a specific no-UI-effect reason, or a concrete blocker/user exclusion in the verification record. Generated recordings remain ignored artifacts rather than Git content. This requirement does not authorise public deployment or expand the mobile milestone.

## Codename One agent guidance

On 2026-11-19 Robin requested the Codename One usage skill described in the official getting-started guide. The official Initializr authoring skill, references and tools are now vendored under `.agent-skills/codename-one/`, linked from `.agents/skills/codename-one` for discovery and required by root `AGENTS.md` for client work. Source revision and licensing notices are preserved. Project-specific instructions retain the pinned 7.0.282 web build, existing Spring Boot backend and browser audio workaround; this adds guidance without upgrading the framework or configuring simulator/cloud services.

## Local iPhone foreground trial — 2026-11-20

The earlier [native readiness review](native-build-readiness.md) is historical; the [local device trial guide](local-device-trial.md) now documents the working route. Robin selected an iPhone 15 Plus running iOS 27.2.1 and authorised local native packaging, development API-origin configuration and foreground playback. Cloud builds, public deployment and dependency upgrades remain out of scope without asking.

**Implemented:** pinned Codename One 7.0.282 local `ios-source` generation, Java framework media shared with the desktop simulator, explicit private IPv4 build-time origin, a single-host iOS HTTP exception, and Local Network permission text. Browser origin/audio remain unchanged. Leaving the iPhone foreground releases media and refreshes the controls; returning restarts the selected track when Play/Retry is pressed. This is not background playback.

**Verified:** signed arm64 builds, USB installation and app launch on the selected iPhone. Robin confirmed catalogue loading, audible playback, pause/resume, immediate playing/paused seeks, enqueue continuity, Previous/Next, automatic advance, Clear queue stopping sound, search and catalogue network-error/reconnect/Reset recovery. Search/Enter/Reset now select Library so results are visible from Queue. Automated browser/simulator checks pass; detailed commands and dated evidence are in [verification](verification.md).

**Remaining:** final physical layout/long-metadata acceptance and a saved physical-device recording with sound. The latest sleeve/ellipsis follow-up build is installed, but its automatic launch was blocked by the phone being locked; those final visual changes have browser/simulator evidence rather than fresh phone acceptance. Background/system audio controls, in-stream interruption recovery and CarPlay remain separate trials.

### Local album trial follow-ups authorised 2026-11-20

Robin authorised importing the two albums currently in `$HOME/Uploads` through the existing Java CLI into the private local service: 12 Oasis tracks and 12 Roxy Music tracks. This is a bounded exception to the synthetic-only development-library default, not authorisation to import the full collection or expose it publicly. The 24 originals remain untouched; imported copies and the two synthetic tracks are retained in the ignored `data/device-trial-albums/` directory. This library is persistent and is no longer owned by the disposable demo helper. The API's ordinary loopback default remains unchanged; the phone trial deliberately serves this library on the selected private interface.

Robin requested distinct demo sleeves and direct Queue navigation from the player's (…) control. Generated fallback sleeves now use track titles consistently across library/player/mini-player rather than content hashes: Morning Circuit is yellow and Evening Signal blue. The (…) action opens the existing Queue view; the header information action retains track details. These remain original placeholder illustrations: real album-cover extraction, album grouping and an album browser have not been added. Automated checks and demonstration recordings continue to use synthetic material.

## Background playback and embedded artwork — authorised 2026-11-20

Robin requested continuing mobile playback when switching apps or locking the phone, plus server/client support for embedded music artwork. Work starts from merged PR #7 (`0464d6b`, latest `origin/main` fetched on 2026-11-20). These requirements supersede the foreground-only release policy above and the earlier artwork deferral. Keep the local iPhone route, pinned dependencies and existing Java queue/browser audio bridge.

The client now retains media on backgrounding, declares `ios.background_modes=music` (generated `UIBackgroundModes: audio`), and advances the queue from the native completion callback independently of the visible form timer. Clear/replacement/destruction still release media. Backgrounding a paused player does not request playback. Robin confirmed app switching, screen lock, background queue advancement and paused-state preservation on the iPhone. The initial phone artwork check failed, and Robin requested fixing the visible but inactive lock-screen controls and missing metadata. That follow-up adds a narrow iOS MediaPlayer bridge for play/pause, adjacent-track navigation, seek and title/artist/album/artwork; Java retains playback/queue policy. Robin confirmed system play/next/previous and stopping sound on Clear. A subsequent direct Device Hub check on the physical iPhone verified Roxy covers in Library, Now Playing and the lock screen, plus title/artist and system pause. The Oasis MP3s and synthetic demo clips contain no embedded image and correctly retain generated sleeves. The seek follow-up preserves known duration while native media opens and avoids republishing the system timeline during normal polling. Robin confirmed the final iPhone lock-screen scrub jumps immediately while paused (remaining paused) and while playing. Dated evidence and remaining platform limits are tracked in [verification](verification.md). Android, CarPlay and interruption recovery remain unverified.

The server derives bounded PNG covers from embedded ID3v2 JPEG/PNG images through `GET/HEAD /api/tracks/{id}/artwork`. Existing files work without reimport or a database migration. Original audio, IDs and edited catalogue metadata remain unchanged. The client loads covers asynchronously for library, player and mini-player, preserving generated sleeves for absent/invalid/unavailable art and ignoring late results for a previous selection. That embedded-artwork increment did not include album grouping or external covers; the authorised fetched-artwork follow-up below now adds explicit track selections and cover acceptance. Tag rewriting remains excluded.

Robin explicitly approved restarting the existing private trial service at `192.168.1.205:18082` against the same retained two-album library during this task. That library remains in the earlier local worktree; it was not copied into Git or replaced with test data. Automated tests and the demo recording use a disposable synthetic catalogue.

## Current progress and next proposal — 2026-11-20

- **Completed and verified:** iPhone background playback/locked queue advance; embedded-cover API/client; native title/artist/artwork, play/pause/next/previous and paused/playing lock-screen seek. That earlier increment passed 38 API/CLI tests, five client tests, browser audio/navigation regressions, local web generation and signed iPhone build/install. Physical results and limitations are in [verification](verification.md).
- **Earlier review checkpoint:** [PR #8](https://github.com/teggr/normal-bias/pull/8) merged as `e33e28b`; the fetched-artwork increment starts from that state. Deployment remains out of scope.
- **Next-step progress tracking:** Robin requested the [album matching and missing artwork proposal](album-matching-proposal.md). It contains milestone statuses and acceptance gates. Its first increment is now authorised and implemented with API/browser evidence, as recorded below and in the proposal progress table. Bulk backfill and automatic acceptance remain deferred; physical fetched-artwork refresh was excluded from this session.
- **Still outside verified scope:** Android, CarPlay, call/headphone interruption recovery, prolonged background reliability and a saved physical-device recording with sound. The final synthetic browser recording includes actual application audio; its limits are recorded separately.

## Desktop simulator increment

On 2026-11-19 Robin narrowed the immediate next step to a demoable simulator experience before a physical-device build. The Java client now has simulator-only framework media playback, a loopback API-origin launch override and compact layouts for phone skins while preserving wide browser layouts and its browser audio bridge. Local network failures remain inline without automatic cloud reporting/account prompts. A launcher and disposable synthetic-demo helper are provided; see [simulator guide](simulator-poc.md) for the tested launch and runtime limits. The Linux simulator was exercised with a software sound output and recorded with actual decoded application audio. At that simulator checkpoint no cloud account, native build, signing, deployment or physical-device verification had been performed. The subsequent local iPhone foreground trial above now shares its framework adapter and verifies private API reachability.

## Progress checkpoint — 2026-11-20 (before this follow-up)

- **Delivered in this increment:** local iPhone source/signing/install route; private trial origin/network policy; foreground framework audio; search-to-Library navigation; direct (…)→Queue navigation; consistent yellow Morning/blue Evening fallback sleeves; and a retained private 26-track library containing the explicitly authorised two real albums plus two synthetic tracks.
- **Verified:** 36 API/CLI tests, three client tests, Java/CSS/compliance and local web builds, complete browser audio/navigation/failure regressions, simulator flows, and signed native builds/installations. Robin separately confirmed the physical foreground playback/queue/search/catalogue-recovery checklist. These evidence types are distinguished in [verification](verification.md).
- **Review state:** implementation is on `codex/local-device-trial`, based on the trial plan from merged PR #6. The earlier Graphite implementation was merged in PR #5. Those local trial changes subsequently merged in [PR #7](https://github.com/teggr/normal-bias/pull/7). The background/artwork follow-up now starts from that merged state; deployment remains out of scope. Music, H2 files, signing assets, native build output and recordings remain outside Git.
- **Evidence and limits:** verified task-local simulator audio and browser navigation recordings have been delivered in chat. No physical phone recording was supplied. Latest sleeve/ellipsis phone acceptance, long-metadata/layout acceptance, album browsing, Android, safe-area/back/accessibility, installed-PWA caching, background/headset/system controls and CarPlay remain outstanding. Favourite/repeat/volume remain proposals.

## Open decisions

### Client visual direction (approved 2026-11-19)

Robin requested a cohesive design system inspired by the linked Retro Music Player and Daft Punk designs, prioritising simplicity, clean lines and platform-native styling. Present mockups and design features for feedback before applying the restyle. The [Graphite proposal](design-system-proposal.md) records the candidate palette, components, platform treatments and implementation sequence. Robin subsequently rejected the flat interpretation and clarified a dark old-school iPod-like player with tactile controls and spacious dark lists. Robin approved the revised Graphite design on 2026-11-19. Apply its tactile graphite player, inset display and spacious dark lists to the existing web client and desktop simulator. Favourite/repeat/volume remain separate feature proposals; preserve existing playback and queue behaviour during the restyle. Existing web/simulator support and mobile/CarPlay feasibility limits remain unchanged.

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

### Reference-based refinement

The Graphite styling increment is implemented in shared Java/CN1 components and CSS, with a wide player sidebar, compact dedicated player, responsive library actions and placeholder record artwork. No API contract, audio bridge or queue-policy changes are included. The subsequent iPhone foreground trial is recorded above; Android, large accessibility-text and CarPlay validation remain future work.

Robin rejected the first rendered Graphite implementation as janky and requested refinement until it matched the supplied screenshots. The refinement replaces the card-and-labelled-controls layout with a full player face, proportioned artwork, thin timeline, recessed wheel and separate circular controls; library rows use title/artist copy and a small queue action. The reference screenshots, rather than the earlier HTML specimen alone, are the visual acceptance basis. Existing feature scope remains in force.

## Keeping this document current

Record later user-approved constraints and decisions here. Clearly label proposals and unresolved questions. Preserve the distinction between the long-term product and the currently authorised work.

## Album matching increment authorised — 2026-11-20

Robin authorised the first end-to-end increment in [album matching](album-matching-proposal.md): MusicBrainz release candidates, Cover Art Archive previews, explicit acceptance and separately persisted/provenanced covers that fill missing embedded artwork. The cover must refresh Library/player/mini-player/iPhone lock screen without reopening audio or changing position/queue. Default to applying to suggested same-artist/album tracks, with count visible on acceptance and optional adjustment; no separate track-confirmation step. Incomplete/mixed metadata must not silently merge albums. Exact edition uncertainty stays visible; no automatic choice or representative release-group fallback. Existing metadata-only evidence is sufficient for this increment, with missing year/disc/order signals disclosed; no automatic audio rescan.

Implementation is complete on `codex/album-matching`, based on freshly fetched main `e33e28b`; API/browser acceptance is verified, and physical artwork-refresh acceptance remains excluded. Bulk backfill, automatic acceptance, deployment, cloud builds and dependency upgrades remain deferred. A bounded live-provider check of the imported Oasis artist/album metadata is authorised; audio transmission is excluded. Synthetic fixtures/mocked providers are required for automated tests and recordings. Robin later excluded iPhone access to avoid conflicting sessions and approved browser validation against a separate disposable API. API/browser checks and a verified synthetic recording with actual client audio pass; the iOS refresh path has not been newly built/installed or physically checked. The retained private service/library was untouched. Actual evidence and the remaining physical gate are recorded in verification.md. A PR is authorised after implementation/verification, without merge.

The implementation is submitted in [PR #9](https://github.com/teggr/normal-bias/pull/9), without merge. API/browser checks pass; the explicit iPhone exclusion and deferred bulk work remain part of its scope.

Robin subsequently requested validating embedded artwork against the real Uploads albums. A separate disposable loopback API confirmed 12/12 Roxy embedded covers and 12/12 Oasis missing covers; all 24 source/served audio hashes match. No external lookup, fetched-cover acceptance, iPhone interaction or change to the retained trial service/library occurred. This bounded manual check does not change the synthetic-only automated-fixture/recording rule.
