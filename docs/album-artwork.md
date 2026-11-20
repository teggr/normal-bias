# Match an album and fill missing artwork

The first increment uses MusicBrainz release search and Cover Art Archive release front covers. It runs in the local API; no audio is sent to either provider. Java 21, Maven, H2, Codename One 7.0.282 and ordinary loopback defaults are unchanged. See the [progress/decisions](album-matching-proposal.md) and dated [verification](verification.md).

## Background service flow

Artwork discovery runs automatically in the API, whether or not a listening client is open. It scans the existing catalogue and later uploads after a five-second startup delay, then waits 30 seconds between passes. The app has no picture action, matching screen, preview or acceptance step. Now Playing's information button shows track details.

Embedded JPEG/PNG takes priority, then a stored fetched cover, then the generated sleeve. Tracks with either existing cover are not looked up or replaced. Missing artist/album tags are skipped until a catalogue metadata edit. Missing covers are grouped by case-insensitive trimmed artist/album tags, with at most 200 due tracks per lookup and 25 seed tracks per pass. Large albums continue in subsequent passes. Only artist/album metadata is sent to MusicBrainz; no audio or file paths leave the service.

Automatic selection requires matching artist and album names after Unicode/case/punctuation normalization. If any candidate matches the tagged local group track count, restrict to those candidates first (excluding same-name singles/promos). Distinct nonempty release-group IDs among the remaining matches are treated as ambiguous and left alone. For an unambiguous album, use provider order; try up to three exact candidates for a usable cover. This chooses representative album artwork, not a verified pressing/edition: year, disc/order, track names and durations are not compared. No fuzzy title/artist choice or release-group image fallback is made. Provenance records that the selection was automatic and its limitations.

Flyway V4 stores per-track checks/retries and a global artwork revision. Existing covers and missing-tag results stay complete until metadata changes. No-match/ambiguous/no-cover results retry after seven days; HTTP 429/503 and shared provider cooldowns retry after the provider deadline (at least 60 seconds, respecting longer Retry-After values up to a day), plus a one-second margin. Other read/publication failures back off from 15 minutes up to one day. A worker claims a 15-minute durable lease before checking/fetching; a crash resumes unfinished work after expiry. Metadata edits make a track eligible again immediately. Concurrent edits/deletion/cover changes invalidate the captured selection before commit. Audio bytes and catalogue tags are never rewritten.

Visible clients check the lightweight revision endpoint at most every ten seconds through the same form heartbeat used for playback progress. Returning to the foreground checks immediately. A changed revision invalidates cached images and in-flight results, then refreshes the existing Library/player/mini-player components in place: library search, pagination, scroll and focus, plus the queue contents, stay intact. A newly decoded cover for the current track also refreshes native system artwork without reopening audio or republishing the timeline.

Revision notifications are an optimisation, not the only recovery mechanism. A cached 404 expires after 30 seconds; transient image failures expire after five seconds. Visible components retry missing images, so a missed revision response cannot preserve a placeholder indefinitely. Image and revision requests have a 25-second state watchdog in addition to transport timeouts. Late results cannot replace a newer request/revision/selection. A callback error in one view is logged without blocking the other views. The decoded-image cache remains bounded to 32 entries, and offscreen expired requests are released by the heartbeat.

Suspended iOS clients recheck on foreground return; this is not push notification or a guarantee of immediate lock-screen refresh while suspended. Browser recording and physical-device checks must be distinguished in the verification record.

Configuration (ordinary loopback defaults remain):

| Property | Default | Purpose |
| --- | --- | --- |
| `normalbias.artwork.background-enabled` | `true` | Disable only for isolated/offline operation or tests |
| `normalbias.artwork.initial-delay-ms` | `5000` | Delay before the first background pass |
| `normalbias.artwork.scan-delay-ms` | `30000` | Delay after each completed pass |

For example, append `--normalbias.artwork.background-enabled=false` when running a synthetic catalogue without a mocked provider. Existing API regression tests explicitly disable the worker; the artwork browser fixture enables it against a test-only synthetic provider.

## API

All requests remain local and unauthenticated. The earlier explicit-selection endpoints remain available for compatibility and diagnostics, but the app does not call them. Collection routes use `items`, `offset`, `limit`, `total`; default suggestions limit 50, candidates 5, maximum 200. A search returns at most 10 MusicBrainz candidates.

| Method and route | Behaviour |
| --- | --- |
| `GET /api/album-artwork/revision` | Durable global artwork revision as `{"revision":"<number>"}`, with `Cache-Control: no-store`; passive client refresh only |
| `GET /api/album-artwork/suggestions/{trackId}?offset=0&limit=50` | Same artist/album hints and an uncertainty warning; missing tags yield a singleton |
| `POST /api/album-artwork/matches` | Body `{"trackIds":["<track-id>"]}`; returns 202 and `{id,state,message,revision,tracks}` |
| `GET /api/album-artwork/matches/{id}` | `LOOKING_UP`, `READY`, `NO_MATCH`, `FAILED`, or `ACCEPTED` |
| `GET /api/album-artwork/matches/{id}/candidates?offset=0&limit=5` | Paginated release candidates, including release-group ID where supplied |
| `GET /api/album-artwork/matches/{id}/candidates/{releaseId}/preview` | Bounded normalized PNG, 404 for absent/unsupported images; no external URLs accepted from callers |
| `POST /api/album-artwork/matches/{id}/accept` | `{"candidateId":"<release-id>","revision":"<lookup-revision>"}`; requires a successfully loaded preview; returns `{revision,trackIds}` |
| `GET /api/album-artwork/tracks/{trackId}` | Accepted-cover provenance and evidence; 404 when there is no accepted association, including when embedded artwork alone is served |
| `GET /api/tracks/{trackId}/artwork` | Embedded first, accepted cover second; 404 otherwise; image-content ETag and private `no-cache` |

Track JSON adds `artworkRevision`. The field remains available to older clients; the listening client uses the global revision in artwork URLs and invalidates positive/missing caches when the service revision changes. Missing responses have `Cache-Control: no-store`. The ETag is now based on actual served image content, replacing the former MP3-ID-only validator. Conditional requests still return 304 for the same image. Old catalogue clients can ignore the additive field.

Any selected track's metadata/artwork change or deletion invalidates an in-flight acceptance with 409, even if edited text was later reverted. Jobs are memory-only, expire after 15 minutes, retain at most 20 snapshots and allow one active search. Expired jobs return 410; start a new match after expiry or API restart. A 422 indicates missing album or inconsistent tags; 400 indicates invalid selection/pagination. Existing accepted associations stay on their explicit track IDs after later metadata edits until a new cover is explicitly accepted. This avoids moving artwork onto a newly inferred group.

## Providers and persistence

The service identifies itself as `NormalBias/0.1 (https://github.com/teggr/normal-bias; local personal music client)`. All provider/redirect requests are serialized and paced at least 1.1 seconds apart. HTTP 429/503 sets a global cooldown of at least 60 seconds, respecting longer Retry-After values up to one day. Individual provider calls do not retry automatically. The background worker schedules durable delayed retries as described above. Legacy explicit lookup snapshots retain misses/missing previews for their 15-minute lifetime. Production provider origins are fixed; local mock origins are available only through a package-private test seam.

MusicBrainz responses are limited to 1 MiB, images to 8 MiB, and bodies have a 15-second read deadline in addition to connection/header timeouts. Covers use the documented CAA `/release/{id}/front-500` route, with at most five requests through HTTPS redirects restricted to MusicBrainz, CAA and `archive.org` destinations. Untrusted hosts, credentials and nonstandard ports are rejected. JPEG/PNG dimensions are checked before decoding (maximum 4096 per side, 16 million pixels) and normalized to at most 512 pixels. The API never downloads images during playback or ordinary track/artwork GETs.

Flyway V3 adds metadata/artwork revisions and separate assets, accepted selections and explicit track associations. Normalized bytes are first durably staged in H2, then written through a forced temporary file and atomic rename under `data/artwork/`. Only after publication does one database transaction commit provenance, associations and revisions. Failed/stale acceptance leaves prior associations intact. Startup reconstructs missing/corrupt files from H2 before serving requests; incomplete acceptance is never auto-applied. Legacy explicit lookup snapshots are not resumed after restart; V4 background leases/retry times survive restarts. This is recoverable application storage, not a power-loss guarantee.

Provenance includes provider, release and release-group/candidate metadata, source CAA route, acceptance time, image hash, selection revision and catalogue evidence. Deleting a track cascades its association only. Shared images, unreferenced assets and selection evidence are retained; safe garbage collection and backup/restore remain deferred. Audio inspection continues to inspect `music/`, not these sidecars. Do not delete the image recovery source manually or edit applied migrations.

## Synthetic checks

With Java 21, FFmpeg, Chrome and the pinned test dependencies installed:

```sh
mvn verify dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test
./client/build-web.sh
npm --prefix client/tests ci
CHROMIUM_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' npm --prefix client/tests test
RECORD_DEMO="$PWD/client/target/recordings/background-artwork" node client/tests/album-artwork.cjs
```

`ArtworkFixtureApplication` exists only in test classes and supplies synthetic provider responses; it is not shipped in the application JAR. Browser scripts start disposable loopback catalogues, use synthetic audio and never touch the retained trial library. Service workers are blocked, so these checks do not establish installed-PWA caching. Generated images/audio/video and H2 data stay ignored. Physical-device, background/system control and CarPlay results must be recorded separately.
