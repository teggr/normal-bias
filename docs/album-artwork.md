# Match an album and fill missing artwork

The first increment uses MusicBrainz release search and Cover Art Archive release front covers. It runs in the local API; no audio is sent to either provider. Java 21, Maven, H2, Codename One 7.0.282 and ordinary loopback defaults are unchanged. See the [progress/decisions](album-matching-proposal.md) and dated [verification](verification.md).

## Client flow

Build the API with `mvn clean verify`, build the client with `./client/build-web.sh`, then serve the generated bundle as described in [client-poc.md](client-poc.md). Choose the image action beside a library track, or the information action in Now Playing.

Tracks with matching artist/album tags are suggested and checked by default. There is no separate confirmation dialog: use **Find candidate releases**, preview a release's cover, then **Accept cover for N tracks**. You may uncheck unrelated tracks before lookup. Suggestions are paginated internally in batches of 50, limited to 200 tracks per match. This is an explicit selection of track IDs, not a permanent catalogue album merge. Missing artist/album suggests only the starting track. A missing album title must be corrected through the existing catalogue edit API before lookup. Mixed artists/albums are rejected; compilations with per-track artists need separate matches in this increment.

Candidates show release artist/title/date/country/disambiguation, provider link, provider track count and selected count. These are hints, not proof of a particular edition. Source year, album artist, provider identifiers and disc/track positions are not yet extracted or backfilled; track order/titles/durations are not compared. No representative release-group fallback or automatic choice is made. Reject/no match/provider failure leaves existing covers unchanged. Correct tags or start a fresh lookup to retry. Album art changes never rewrite ID3 tags, reopen audio or alter the finite session queue.

Embedded JPEG/PNG remains first choice, followed by an explicitly accepted fetched cover, then the generated sleeve. Accepting a cover for an album does not replace embedded pictures. Acceptance refreshes the current client's Library, player and mini-player; the shared Java path also requests fresh iOS system artwork without publishing a new audio timeline. Physical-iPhone refresh was excluded from this session at Robin's request. Other already-open clients refresh on a new catalogue load/restart; live cross-session notifications are not implemented.

## API

All requests remain local and unauthenticated. Collection routes use `items`, `offset`, `limit`, `total`; default suggestions limit 50, candidates 5, maximum 200. A search returns at most 10 MusicBrainz candidates.

| Method and route | Behaviour |
| --- | --- |
| `GET /api/album-artwork/suggestions/{trackId}?offset=0&limit=50` | Same artist/album hints and an uncertainty warning; missing tags yield a singleton |
| `POST /api/album-artwork/matches` | Body `{"trackIds":["<track-id>"]}`; returns 202 and `{id,state,message,revision,tracks}` |
| `GET /api/album-artwork/matches/{id}` | `LOOKING_UP`, `READY`, `NO_MATCH`, `FAILED`, or `ACCEPTED` |
| `GET /api/album-artwork/matches/{id}/candidates?offset=0&limit=5` | Paginated release candidates, including release-group ID where supplied |
| `GET /api/album-artwork/matches/{id}/candidates/{releaseId}/preview` | Bounded normalized PNG, 404 for absent/unsupported images; no external URLs accepted from callers |
| `POST /api/album-artwork/matches/{id}/accept` | `{"candidateId":"<release-id>","revision":"<lookup-revision>"}`; requires a successfully loaded preview; returns `{revision,trackIds}` |
| `GET /api/album-artwork/tracks/{trackId}` | Accepted-cover provenance and evidence; 404 when there is no accepted association, including when embedded artwork alone is served |
| `GET /api/tracks/{trackId}/artwork` | Embedded first, accepted cover second; 404 otherwise; image-content ETag and private `no-cache` |

Track JSON adds `artworkRevision`. The client uses it in artwork URLs and invalidates both positive and missing-image caches after acceptance. Missing responses have `Cache-Control: no-store`. The ETag is now based on actual served image content, replacing the former MP3-ID-only validator. Conditional requests still return 304 for the same image. Old catalogue clients can ignore the additive field.

Any selected track's metadata/artwork change or deletion invalidates an in-flight acceptance with 409, even if edited text was later reverted. Jobs are memory-only, expire after 15 minutes, retain at most 20 snapshots and allow one active search. Expired jobs return 410; start a new match after expiry or API restart. A 422 indicates missing album or inconsistent tags; 400 indicates invalid selection/pagination. Existing accepted associations stay on their explicit track IDs after later metadata edits until a new cover is explicitly accepted. This avoids moving artwork onto a newly inferred group.

## Providers and persistence

The service identifies itself as `NormalBias/0.1 (https://github.com/teggr/normal-bias; local personal music client)`. All provider/redirect requests are serialized and paced at least 1.1 seconds apart. HTTP 429/503 sets a global cooldown of at least 60 seconds, respecting longer Retry-After values up to one day. There are no automatic retries. Search misses and missing cover previews are retained within the 15-minute job; retry requires a new job. Production provider origins are fixed; local mock origins are available only through a package-private test seam.

MusicBrainz responses are limited to 1 MiB, images to 8 MiB, and bodies have a 15-second read deadline in addition to connection/header timeouts. Covers use the documented CAA `/release/{id}/front-500` route, with at most five requests through HTTPS redirects restricted to MusicBrainz, CAA and `archive.org` destinations. Untrusted hosts, credentials and nonstandard ports are rejected. JPEG/PNG dimensions are checked before decoding (maximum 4096 per side, 16 million pixels) and normalized to at most 512 pixels. The API never downloads images during playback or ordinary track/artwork GETs.

Flyway V3 adds metadata/artwork revisions and separate assets, accepted selections and explicit track associations. Normalized bytes are first durably staged in H2, then written through a forced temporary file and atomic rename under `data/artwork/`. Only after publication does one database transaction commit provenance, associations and revisions. Failed/stale acceptance leaves prior associations intact. Startup reconstructs missing/corrupt files from H2 before serving requests; incomplete acceptance is never auto-applied. Lookup snapshots are not resumed after restart. This is recoverable application storage, not a power-loss guarantee.

Provenance includes provider, release and release-group/candidate metadata, source CAA route, acceptance time, image hash, selection revision and catalogue evidence. Deleting a track cascades its association only. Shared images, unreferenced assets and selection evidence are retained; safe garbage collection and backup/restore remain deferred. Audio inspection continues to inspect `music/`, not these sidecars. Do not delete the image recovery source manually or edit applied migrations.

## Synthetic checks

With Java 21, FFmpeg, Chrome and the pinned test dependencies installed:

```sh
mvn verify dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test
./client/build-web.sh
npm --prefix client/tests ci
CHROMIUM_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' npm --prefix client/tests test
RECORD_DEMO="$PWD/client/target/recordings/album-matching" node client/tests/album-artwork.cjs
```

`ArtworkFixtureApplication` exists only in test classes and supplies synthetic provider responses; it is not shipped in the application JAR. Browser scripts start disposable loopback catalogues, use synthetic audio and never touch the retained trial library. Service workers are blocked, so these checks do not establish installed-PWA caching. Generated images/audio/video and H2 data stay ignored. Physical-device, background/system control and CarPlay results must be recorded separately.
