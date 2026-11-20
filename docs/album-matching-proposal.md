# Album matching and missing artwork proposal

Date: 2026-11-20. Status: **first end-to-end increment authorised 2026-11-20; implemented and verified through API/browser; physical artwork refresh excluded this session**.

Robin requested a next-step proposal and progress tracking after the background playback and embedded-artwork increment. Robin authorised milestones 1–4, MusicBrainz matching and Cover Art Archive covers, with explicit cover acceptance. Bulk backfill and automatic acceptance remain deferred. Original MP3s and catalogue metadata remain unchanged.

## Progress and evidence

| Work | Status | Evidence / next gate |
| --- | --- | --- |
| iPhone background playback and locked queue advance | Implemented; physical checks passed | [Verification record](verification.md), selected iPhone 15 Plus / iOS 27.2.1 |
| Embedded artwork API and client rendering | Implemented; API/browser/phone checks passed | Roxy covers observed in Library, player and lock screen |
| iOS system metadata, play/pause/next/previous and seeking | Implemented; physical checks passed | Robin confirmed final paused and playing lock-screen seeks |
| Source-file artwork investigation | Complete | Roxy: 12 embedded covers; Oasis and demo clips: 14 files without embedded pictures |
| Album matching and fetched-artwork design | Proposal written | Review the recommended first milestone and open decisions below |
| Album candidate matching / preview / acceptance | Implemented; API/browser verified | Synthetic flow and bounded live lookup passed; iPhone excluded this session |
| Automatic bulk artwork backfill | Deferred | Follow after single-album matching and cache refresh are verified |

The earlier embedded-artwork implementation and initial proposal were delivered in [PR #8](https://github.com/teggr/normal-bias/pull/8), now merged as `e33e28b`. This fetched-artwork increment is a separate review checkpoint, without merge or deployment.

## Problem and recommended first milestone

Normal Bias currently serves embedded ID3v2 JPEG/PNG images, otherwise showing a generated sleeve. Some original MP3s have no embedded image even when another application displays an album cover. The imported Oasis files contain artist, album, year and AMG/Windows Media identifiers but no embedded picture or artwork URL. A screenshot from another app does not establish whether its cover came from lookup, cache or a different copy of the album.

Implement **on-demand matching for one provisional album, preview, and explicit acceptance of a cover** first. A user should be able to request candidates, inspect the release/edition and cover, accept one, and see that cover on the album's uncovered tracks in the app and iPhone system player. Rejecting or failing a lookup must preserve the current artwork and playback. No bulk auto-accept in this first milestone.

Keep Spring Boot, Java 21/Maven, H2, local file storage and the current API/client architecture. The client remains Java/Codename One 7.0.282 with the existing narrow platform bridges. No cloud build, public deployment, MP3 rewriting or audio upload to a matching provider is proposed here.

## Matching and provider approach

Robin approved MusicBrainz for album/release candidates and Cover Art Archive for front images for this increment. Use a server-side provider interface so another source can be added without changing playback or the client.

1. Build a provisional album membership set from existing catalogue metadata. Normalize text for comparison only; preserve displayed metadata. Prefer album artist when available. Artist plus album title is a starting grouping hint, not a reliable permanent album identity: compilations, different editions, discs and identically named albums need explicit handling. Tracks with missing or inconsistent metadata must remain separate/unmatched rather than being guessed into an album.
2. Use existing MusicBrainz identifiers if present. Otherwise search by artist and album, then compare available year, track count, track titles/order and durations. Distinguish the album concept (release group) from a particular edition (release). Prefer a matching release's cover; a representative release-group cover should be labelled as such in the preview.
3. Preserve AMG identifiers as possible evidence. The Oasis `AMGa_id=R 224033` and `AMGp_id=P 44888` are not MusicBrainz IDs; do not invent a direct conversion or rely on an unverified crosswalk.
4. Show a small paginated candidate list with artist, album, edition/year where available, track-count compatibility, cover preview and a provider link. A provider search score alone is not sufficient evidence for silently choosing an edition. Explain conflicts or missing evidence simply.
5. Accept one candidate explicitly. Revalidate the provisional album membership and current artwork revision before applying it. A stale selection after a concurrent metadata edit should request a fresh match rather than silently applying to different tracks.

**Existing data limitation:** track JSON/H2 currently persist title, artist, album and duration, but not year, album artist, disc/track numbers or provider identifiers. Those additional signals must be extracted and stored deliberately with new Flyway migrations or an album-matching evidence table; they are not already available from the catalogue endpoint. Avoid a full music rescan during startup or an ordinary library GET. Make any backfill bounded, observable and resumable. The first increment can operate with incomplete evidence and show that uncertainty.

Provider references checked on 2026-11-20:

- [MusicBrainz search API](https://musicbrainz.org/doc/MusicBrainz_API/Search)
- [MusicBrainz application identification and rate limits](https://musicbrainz.org/doc/MusicBrainz_API)
- [Rate limiting details](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting): at most one MusicBrainz request per second from this service unless separately agreed, with a meaningful application/contact User-Agent; back off on throttling and transient failures.
- [Cover Art Archive API](https://musicbrainz.org/doc/Cover_Art_Archive/API): front-cover/release-group discovery, thumbnails, redirects and missing-art responses. Follow the documented service route rather than constructing an Internet Archive storage URL.

## Store and serve artwork independently of audio

Download a selected cover once and store a validated, normalized local image shared by the accepted album membership. Keep original MP3 bytes, content IDs and edited catalogue metadata unchanged. Store provider, matched release/group identifier, source URL, selection time, image hash and artwork revision in H2. Artwork files should live outside `music/` so audio inspection does not mistake them for orphaned MP3s.

Use bounded downloads, timeouts and the existing image size/dimension checks. Fetch only from the provider integration's expected image/redirect destinations. Publish a selected image and its database reference in a recoverable way: failed download/decode/publication must leave the last valid cover intact. Restart should retain accepted covers and retry unfinished work safely. Specify reference cleanup for deleted tracks/albums without deleting a cover still used elsewhere.

Recommended resolution order is: explicit user override (if a later override feature is approved), per-track embedded image, accepted fetched album image, generated sleeve. Thus accepting an album cover initially fills uncovered tracks; it does not silently replace a track's embedded picture. Replacing embedded art or adding manual uploads remains an explicit product decision.

Continue serving images through the current track artwork endpoint. Its current ETag derives from the immutable MP3 ID, which is insufficient for replaceable external artwork. Introduce an artwork revision/content validator and client invalidation so existing browser/native image and missing-art caches refresh after acceptance. Refresh Library, Now Playing, mini-player and lock-screen art without reopening audio, changing playback position or rebuilding the queue. Do not fetch external artwork synchronously from this endpoint or during playback.

## Suggested API and user flow

These capabilities are now implemented; the exact routes and limits are in [album-artwork.md](album-artwork.md):

- Start a match for a selected provisional album; return a job identifier/status.
- Read paginated candidates and bounded cover previews through the service.
- Accept a candidate with expected album/artwork revisions; return the resulting artwork revision.
- Read status and retry a failed lookup without losing a previous selection.

Keep the current track browser; this increment does not require a full album-browser restyle. Add one contextual “Find album artwork” entry, a compact candidate/preview view and clear loading/no-match/error/accepted states. Keep matching outside the playback path. A later album browser can build on the persisted explicit selection/artwork associations; a permanent album entity is deferred.

## Milestones and acceptance gates

| Milestone | Deliverable | Acceptance gate | Status |
| --- | --- | --- | --- |
| 1. Evidence and provisional albums | Additive schema/evidence, explicit membership and grouping rules | Missing metadata, compilations, duplicate album titles, edits and multi-disc examples handled without silent merges; paginated access | Implemented; see scoped evidence below |
| 2. Single-album candidate lookup | MusicBrainz/CAA integration behind a provider adapter; preview and job state | Correct/no-match/ambiguous/offline/throttled results; rate limiting and bounded retry; no audio file transmission | Implemented; mocked tests and bounded live lookup pass |
| 3. Accept and persist a cover | Recoverable local storage, provenance, revisioned track-artwork response | Survives restart; stale acceptance rejected; original MP3 IDs/bytes unchanged; shared cover deletion is safe | Implemented; recovery and real process-restart checks pass |
| 4. Client and physical acceptance | Contextual lookup/preview/accept and cache invalidation | New cover appears in Library/player/mini-player/iPhone lock screen while playback and queue continue; browser and phone checks plus required recording evidence | API/browser verified and recorded; iPhone explicitly excluded this session |
| 5. Bulk missing-artwork job | Paginated progress/findings, cancellation/resume and optional conservative auto-match policy | One lookup per album, bounded work for the full collection, cached misses, retries and explicit ambiguous results | Deferred |

Use synthetic local provider responses for automated tests, including known/ambiguous editions, missing images, malformed and excessive images, timeout/429/503, provider redirects, concurrent edits/deletion and process restart. Keep automated tests and recordings free of personal music. A separately documented live-provider smoke test should verify real API compatibility before claiming provider support; the authorised bounded Oasis example now passes as recorded below. Run the existing API/CLI, browser and player regressions, then the selected iPhone's artwork-refresh/background/system-seek checklist when device access is authorised again. Browser results do not establish mobile behavior.

## Resolved decisions and deferred work

- MusicBrainz/CAA are approved for this increment; cover acceptance is always explicit.
- Robin approved defaulting to suggested same-artist/album tracks without a second confirmation dialog, with affected count visible and optional unchecking.
- Catalogue-only evidence is the bounded first increment. Do not infer compilation membership or claim source year/disc/order comparison. No album merge or startup rescan is performed.
- Only release-specific covers are offered; representative release-group fallback and manual embedded-art overrides remain deferred.
- The repository URL supplies the provider contact; pacing, cooldown and job-cache policy are documented below.
- One PR with staged commits is the review unit. Bulk backfill/automatic acceptance remain deferred.
- Robin subsequently excluded iPhone access to avoid conflicting sessions and approved browser validation against a disposable API. New physical lock-screen refresh/background/seek acceptance therefore remains unverified in this session.

Update this progress table and the project brief as each milestone is authorised, implemented and verified. Suggestions remain proposals until explicitly agreed.

## Authorised increment — 2026-11-20

Use MusicBrainz releases and Cover Art Archive release front covers. No representative release-group fallback in this increment: when the exact edition is uncertain, label the evidence limitations and require a release-specific explicit cover acceptance. Robin approved defaulting to all suggested tracks without a separate membership-confirmation step; the accept button states the affected count, and the list can be adjusted. Suggestions use matching artist/album tags only and do not create or merge permanent catalogue albums. Missing metadata stays a singleton; mixed artist/album selections are rejected. Compilations with per-track artists must be matched separately for now. The first increment intentionally uses existing catalogue evidence and does not rescan audio or claim year/disc/order comparison.

Implementation stages: additive V3 schema and recoverable image publication; bounded provider adapter and job API; Java contextual client flow and revision refresh; synthetic regression/browser recording and selected-iPhone acceptance. One PR will contain reviewable staged commits; no merge. Lookup jobs expire after 15 minutes and retain at most 20 snapshots. At most one search runs at once; requests are paced at 1.1 seconds and 429/503 impose a Retry-After-aware cooldown. Retries are user-initiated. Missing covers are cached within a job, not permanently. The User-Agent identifies NormalBias/0.1 and the public repository contact URL. A bounded live Oasis lookup is authorised for artist/album metadata only; accepting a personal-library cover remains an explicit user action.

Accepted selections retain candidate/release-group metadata, source URL, timestamp, normalized image hash and the catalogue/revision snapshot. Image bytes are durably staged in H2 and materialized atomically outside music storage before associations are committed. Startup reconstructs missing/corrupt image files from the durable source. A failed/stale acceptance leaves the prior association intact. Unreferenced assets/selections are retained as recovery/provenance evidence; automatic garbage collection is deferred. Deletion cascades track associations only, preserving shared covers. Accepted associations remain attached to their explicit track IDs after later metadata edits; any new in-flight acceptance is invalidated by metadata/artwork revision changes. No ID3 tags are written.

## Delivered verification scope

The browser increment is implemented: candidates/previews/explicit acceptance, immutable MP3s/metadata, separately persisted provenance, restart recovery, and Library/player/mini-player revision refresh with uninterrupted audio/queue. Synthetic checks cover visible no-match, provider failure, stale acceptance and completion while matching. The bounded live Oasis lookup returned 10 editions and three usable previews; no personal cover was accepted. A verified synthetic recording with actual client audio is delivered under `client/target/recordings/album-matching/demo.mp4`. See [verification.md](verification.md) for commands, measured checks and limitations.

Milestone 1 is intentionally scoped to explicit track snapshots and existing catalogue evidence, rather than new tag extraction or a permanent album entity. Milestone 4 remains **partially accepted**: browser verified, shared iOS refresh path implemented but no new native build/install/physical lock-screen refresh/background/system-seek checks, following Robin's explicit exclusion. Existing phone results predate this change and are not new acceptance. Bulk matching, automatic acceptance, richer evidence/backfill, representative group art and cleanup remain deferred.

Review checkpoint: [PR #9](https://github.com/teggr/normal-bias/pull/9) is open for this increment. No merge performed.
