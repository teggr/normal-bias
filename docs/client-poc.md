# Web listening-client PoC

The Codename One client lives in `client/`, with a separate Maven reactor and Java 17 source compiled using a Java 21 JDK. The API and uploader remain Java 21. Building the API does not build the client. Codename One 7.0.282 is pinned in the client POM.

The shared Java web/iPhone client supports server-discovered login, remembered sessions, logout and scoped playback URL renewal. Serve the generated web assets from the same HTTPS API origin to use authentication; see the client flow in [authentication](authentication.md#listening-clients). The commands below deliberately use loopback-only demo mode for synthetic fixtures. See [authentication](authentication.md); do not expose demo mode or use a private-interface HTTP workaround for the new secured API.

## Build and run locally

Requires Java 21 JDK, Maven 3.6.3+, Bash, `unzip` and internet access to Maven Central, `repo.codenameone.com` and Codename One's official tool downloads. The first build installs vendor tools under `~/.codenameone`; this cache must be writable. No Codename One account, cloud build credits or paid plan is needed for the local JavaScript target used here.

From the repository root:

```sh
mvn clean verify
./client/build-web.sh
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar \
  --normalbias.auth.mode=demo \
  --spring.web.resources.static-locations="file:$(pwd)/client/target/web/"
```

Open `http://127.0.0.1:8080/`. In another terminal, upload the bundled synthetic fixture to start:

```sh
java -jar cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar api/src/test/resources
```

The web build script runs the client JUnit tests and compatibility check, translates Java to JavaScript locally, and replaces only the generated `client/target/web/` directory with the extracted static bundle. All generated output is ignored by Git. Static-resource serving is configured at launch; required authentication remains the normal server default. Keep the trailing slash in the static-resource location. Do not serve the repository root as the web root.

To keep the demo separate from an existing library, add `--normalbias.data-dir=/absolute/path/to/disposable-data` to the API command. To avoid a port conflict, add `--server.port=8090`, open that port in the browser and pass `http://127.0.0.1:8090` as the CLI's second argument. Keep the API's loopback binding. This is still an unauthenticated local PoC.

The client derives the API origin from the browser page URL, so catalogue and audio requests go straight to `/api/tracks` and `/api/tracks/{id}/audio` on the same server. It does not need a CORS proxy or CORS permissions. Opening the extracted HTML with `file://`, or serving it on an unrelated origin, is not supported by this increment.

Codename One generates a PWA app-shell cache. After rebuilding, close existing tabs for the app and reopen it so a waiting worker can activate. If an earlier build is still shown, clear that local site's data or use a fresh browser profile. App-shell caching is not offline music support; the catalogue and audio need the API.

## Graphite interface

The canonical [design reference](../DESIGN.md) governs all client UI. The retained player follows the supplied reference proportions: an edge-to-edge graphite player, artwork around 70% of its width, a compact inset metadata strip, thin white seek rail with elapsed/remaining times, a recessed transport wheel and four separate circular controls. The central wheel handles Previous, Play/Pause and Next. The upper-left outer control toggles Player information in place of artwork, changing from an information icon to a disc named Show artwork. A short horizontal slide swaps the two faces within the same bounds. The adjacent queue-symbol control opens Queue. The remaining outer controls are Restart track (lower left, switching to Retry track only on playback failure) and Shuffle queue (bottom right, replacing the former Library shortcut). Library and Profile controls live in the shared fixed toolbar outside the scrolling artwork/transport area; the form explicitly respects the platform safe area. Queue is a child screen of Now Playing, reached through the dedicated wheel Queue control; its Back action returns to the player. Queue’s toolbar exposes only Back, its title and Shuffle queue; Library and Profile remain on Now Playing. Library has no top-level Queue action. These controls remain reachable on short native screens even when the player body scrolls. All icon buttons retain accessible names; their actions are existing client capabilities.

The library uses continuous dark rows, circular artwork, a title/artist pair and one amber add-to-queue icon. Tap the title/artwork area to play; keyboard and accessibility names include the track title. Search and reset use icons alongside the native text field. The All songs/Albums/Artists selector and a magnifier toggle share one Library row. Search is initially collapsed; Show search reveals the recessed field beneath that row and focuses the editor. Hide search commits editing and collapses the field while preserving entered text and the applied query/results. The magnifier stays amber while the field is open or a filter is applied; Reset clears the filter. Queue and Now Playing have no library search controls. Returning to Library preserves its current search and results. The search well shares the metadata panel’s dark fill, fine rim and rounded corners. All songs/Albums/Artists use the player’s moulded graphite pill edge, with amber text for the active view and white text for the others; the active colour persists when focus moves into search. Root views show their name only in the selector; the contextual heading/back row appears only inside an artist or album. Search filters the catalogue; it does not filter or change the queue. Pagination controls appear when needed. Library and Now Playing are separate top-level destinations at every window width. The shared toolbar switches to the other destination and exposes Profile for authenticated users; the Now Playing navigation mark is a rounded white play-triangle outline with a small amber centre. Profile opens a full-screen Account view with a read-only Username metadata well and the permitted Change password and Sign out actions. The well uses the player’s recessed metadata style, with a muted Username caption and bold wrapping authenticated username. It is account identity, not a role badge. Account has no popup or Close action. Its fixed Back control returns to the launching Library or Now Playing view, preserving playback and presentation state. Cancelling password change returns to Account, retaining that original Back destination. Account hides the mini-player while media continues. Library and Queue retain the mini-player; its metadata opens Now Playing. The full player has no duplicate mini-player. Wide screens centre the existing player at a maximum 62 mm width; short viewports scroll beneath the fixed toolbar. Navigation preserves the Library query/group/page/scroll state and active media instead of reloading it.

Illustrations are deterministic original fallback sleeves selected by track title and shared by the player, metadata and list thumbnails; they remain fallbacks when server artwork is unavailable. Morning Circuit uses yellow and Evening Signal blue regardless of MP3 file hash. The upper-left wheel information button reveals read-only title, artist, album, duration and playback state in the artwork recess. Long values wrap and the panel scrolls independently. Empty queues show a choose-track message; details follow track changes and the selected face survives navigation. Swapping faces preserves position, play/pause intent and the same media resource. Normal playback does not display redundant status prose; loading and error/recovery messages remain visible. The seek rail has a larger interactive target and uses the slider event's captured value so polling cannot replace a user's requested position.

Both Shuffle queue controls perform the same one-shot shuffle of the entire current session queue. The currently selected track moves to its new queue index while continuing with the same media resource, playback position and play/pause intent. Previous/Next and completion use the new order. Shuffle does not automatically select or start a track when the queue has no selection. Empty/single-track queues disable both buttons. Randomness can retain the same order; this is an action, not a persistent shuffle mode. Queue membership and the 200-track bound are unchanged.

## Listening behaviour

- Choose **All songs**, **Albums** or **Artists** in Library. Albums opens its songs; Artists opens albums and then songs. “Library views” steps back out of a group. Switching view or opening/closing a group clears search and starts at page one. Search and Reset retain the current group. Group rows show song counts and a representative track sleeve; absent artwork retains the generated fallback. Album groups use exact artist + album catalogue text, with Unknown labels for missing values. Compilation album-artist grouping and disc/track ordering are not yet supported.
- Browse/search 25 songs, albums or artists at a time, with Back/More, exact matching totals and loading, empty and failure states. Search follows the API's literal substring semantics. Late responses from earlier searches are ignored.
- Tapping a track adds/selects it in the session queue. `+ Queue` appends without interrupting playback; adding an existing track reuses its position. Queue contents survive searches and page changes, and are limited to 200 tracks. The queue is lost on page reload.
- Open Now Playing to use Play/Pause, the seek slider and Previous/Next. Its dedicated Queue control opens the Queue child screen, with artwork, track count, current-track indication and selectable songs. Queue Back returns to Now Playing; choosing a queued track or clearing stays on Queue. Use the Library toolbar icon to return to browsing. Select a queued track or use the player controls. Finishing a track advances to the next; finishing the queue stops. Shuffle queue randomises the current session order once; there is no persistent shuffle mode, repeat, persistent playlist or individual queue removal. Clear queue stops and releases the audio resource.
- The browser receives the existing API's MP3 stream with Range support. Seeking is available after the media duration becomes known. The player reports buffering, browser playback rejection and media failures. Restart track seeks the same current stream to zero without changing playing/paused intent. Empty queues disable it; restarting a completed track opens it paused at zero. Existing playback-grant renewal still applies when necessary. Only terminal playback failure changes the lower-left action to Retry track; loading disables the control and buffering alone leaves Restart. Retry reopens the failed stream using the last known position and intended playing/paused state; recovery restores Restart. Failure remains explicit player state until retry/new playback/clear, so a clean stale snapshot or adding queue entries cannot hide the recovery action. Failed tracks do not automatically skip or retry forever.
- Browser audio policy can require a user gesture; press Play if playback was blocked. Closing the page stops playback. Embedded ID3 covers now load asynchronously across library/player/mini-player; absent or unavailable art keeps the generated sleeve. Native background playback has a separate iPhone trial; iPhone lock-screen commands/metadata use a separate native bridge, while calls/audio focus and offline downloads remain outside the web increment.

## Browser audio integration

UI, catalogue access, queue and playback policy are Java. A small JavaScript native-interface implementation (`client/javascript/src/main/javascript/dev_normalbias_client_player_BrowserAudio.js`) calls the browser's `HTMLAudioElement` on the main thread and returns an atomic state snapshot to Java every 500 ms. It checks that stream URLs belong to this origin and match the audio endpoint, closes old media before opening a new track, and ignores stale events and play promises.

This platform glue is a workaround for an observed Codename One 7.0.282 JavaScript-port failure: `MediaManager.createMediaAsync()` successfully loads the stream, but its HTML5 media wrapper then throws `TypeError: el.play is not a function` against a worker-side host proxy. The PoC therefore does **not** validate Codename One's built-in media adapter. Recheck that adapter on a future framework update before removing the bridge. The native-interface source and generated bundle were verified locally. The JavaScript bridge remains browser-only. The separate iPhone trial selects the framework media API from Java, sharing the simulator adapter. The iPhone now preserves media across backgrounding with the music build hint and native completion-driven queue advance. iOS system commands and metadata now use the narrow `SystemAudio` MediaPlayer interface because the pinned port’s AVKit metadata setters are incomplete. Commands return to Java on the EDT and native state is cleared on release; Android and interruption recovery remain separate work; see [local device trial](local-device-trial.md).

The initial browser increment needed no API changes. The subsequent artwork endpoint serves bounded embedded covers without changing track JSON or stored audio; see [API contract](local-poc.md#embedded-covers). The client shares at most 32 decoded cover entries and coalesces simultaneous requests. Late results cannot overwrite a newer selection. The shared client supports the required-auth HTTPS session and scoped playback-grant flow documented in [authentication](authentication.md); public deployment remains deferred.

## Repeatable checks

```sh
# API/CLI regression suite
mvn clean verify
# Client JUnit queue tests, compatibility check and browser bundle
./client/build-web.sh
# Browser test tooling only: Node 20+, FFmpeg, Chromium and a JDK on PATH
npm --prefix client/tests ci
CHROMIUM_PATH=/usr/bin/chromium npm --prefix client/tests test
```

The Node/Playwright script is test orchestration, not application code or a client build tool. It launches the packaged API on a random loopback port with a new temporary data directory, generates 28 synthetic 12-second MP3s, and drives the actual generated client in headless Chromium. It checks empty library, search/reset, pagination, playback progress, pause/resume, seek, queue navigation/completion, HTTP 206 delivery, forced audio/catalogue failures and recovery, queue clearing and resource release. It blocks service workers to exercise the freshly built assets. It saves a screenshot under `client/target/`; failed runs retain temporary API data/logs for diagnosis. It never opens an existing catalogue or personal music. Test processes stop afterward. Set `CI=1` only in a sandboxed CI environment that requires Chromium's `--no-sandbox` launch flag.

See [verification](verification.md) for the actual environment/results and remaining gaps. Chromium success does not establish Firefox/Safari support, device background audio, system media controls or CarPlay feasibility.

## Agent guidance

Before client work, apply the [official Codename One authoring skill](../.agent-skills/codename-one/SKILL.md) and the project-specific rules in [AGENTS.md](../AGENTS.md). Its references and Java tools are vendored from a pinned upstream revision with [provenance and refresh instructions](../.agent-skills/codename-one/UPSTREAM.md). The current upstream skill can describe newer features than our pinned 7.0.282 release; verify compatibility before using a goal or API. Installing the skill does not configure simulator/cloud MCP, add a Maven wrapper or change the client build.

For the proposed move to a physical device, see the [native build readiness review](native-build-readiness.md). This review does not change the web PoC’s supported platforms.

## Desktop simulator

The authorised simulator increment now provides local playback through the framework media API and compact phone-screen layouts. Use `./client/demo-simulator.sh` for a disposable synthetic library, or `./client/run-simulator.sh` for an existing loopback API. See the [simulator guide](simulator-poc.md) for prerequisites, audio/proxy setup, tested commands and limits. The iPhone foreground trial shares the Java framework adapter; background playback requires physical acceptance recorded separately and does not establish Android support.

Optional browser evidence capture: set `RECORD_DEMO` to an **absolute ignored output directory** when running `npm --prefix client/tests test`. `capture-demo.cjs` records timestamped browser screenshots and the real HTML audio output, aligns their measured clocks and exports H.264/AAC `demo.mp4` using FFmpeg. It adds a silent clock source to preserve pauses, never a replacement soundtrack. This is browser evidence, not proof of iPhone background playback.

## On-demand missing artwork — 2026-11-20

Artwork matching runs in the service automatically. The client has no picture action or matching/preview/acceptance screen; Now Playing information shows track details. Its shared form heartbeat checks the service revision at most every ten seconds; returning to the foreground forces a fresh check. Revision changes refresh existing Library/player/mini-player/system artwork in place, without reloading the catalogue or changing search/scroll/queue/audio state. Missing-image cache entries expire after 30 seconds and transient failures after five seconds, allowing recovery even if revision requests fail. A 25-second watchdog expires stalled image/revision state, late callbacks are ignored, and one failing view cannot suppress another's update. Successful cover downloads notify the current player/system-artwork path. See [album-artwork.md](album-artwork.md) for the detailed policy and [verification](verification.md) for current evidence and platform limits.

## Account links and installed-client behaviour — 2026-11-23

The shared client now supports invitation acceptance, password reset and signed-in password change. Use the commands/link contract in [authentication](authentication.md). Root-page fragment links open browser forms; the native login accepts a pasted link from its configured HTTPS service. There are no universal links. Signing in is explicit after account completion; password change signs out all sessions and stops local playback, while cancellation preserves playback/queue.

The web build now also requires Python 3. `build-web.sh` installs a small early location/history handler and `prepare-web.py` includes it in the generated worker's asset list and recalculates the content version after modifying the HTML. The worker still caches only shell assets, never `/api` responses or music. Real standalone Chrome installation/launch, session restoration, offline retry and logout/reopen were checked with service workers enabled; iPhone Home Screen behaviour remains unverified. See verification.md for results and recordings.

## Compact-navigation regression and demonstration

Use Java 21 and the existing test tooling after building the API and browser client:

```sh
CHROMIUM_PATH='/Applications/Google Chrome.app/Contents/MacOS/Google Chrome' node client/tests/browser-smoke.cjs
RECORD_DEMO=1 node client/tests/compact-navigation.cjs
```

Run `RECORD_DEMO=1 node client/tests/restart-track.cjs` for playing/paused restart, unchanged media source/no reload, real HTTP audio failure and Retry recovery, completed-track restart, empty/cleared state and short-screen reachability. It records actual generated-client audio using a disposable synthetic demo service under `client/target/recordings/restart-track/`.

Run `RECORD_DEMO=1 node client/tests/queue-shuffle.cjs` for both Shuffle entry points, full permutation/selection/media continuity, paused state, Next/Previous/completion order, disabled states and responsive Queue/player checks. It prepares four synthetic clips in a disposable loopback demo service and records actual application audio under ignored `client/target/recordings/queue-shuffle/`.

Run `RECORD_DEMO=1 node client/tests/player-information.cjs` for focused artwork/information checks and an actual-audio recording under `client/target/recordings/player-information/`. It covers the empty face, current-track updates, navigation, unchanged media/paused position, clearing, wrapping/missing metadata and responsive bounds, using its own disposable synthetic demo service.

The focused navigation check launches its own loopback demo-mode API, creates two original synthetic 90-second musical clips and verifies separate Library/Now Playing destinations, nested Queue/back, the dedicated Queue entry and inline information/artwork toggle, mini-player navigation, bounded wide player, resize continuity, the same media stream, preserved paused position/intent and clearing/release. It stops its API afterward. `RECORD_DEMO=1` saves an actual 390×844 browser/app-audio MP4 plus screenshots and state/timing evidence under ignored `client/target/recordings/compact-navigation/`. The capture helper preserves the initial viewport size; no replacement soundtrack is used. Browser smoke continues to cover search, grouping, pagination, seeking, completion and failure/retry. These demo suites block service workers and do not establish native reliability.

Account browser/native helpers open Profile before Sign out or Change password. Existing account-link and server authorisation contracts are unchanged. See [DESIGN.md](../DESIGN.md) for the saved concepts, their precedence and the requirement to retain the real circular player rather than the generated transport alternatives.
