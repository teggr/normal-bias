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

## Service connection and sign-in

The landing page contains **Service address**, **Username** and **Password**. A remembered session connects automatically and opens Library. If the service cannot be reached, the same form shows the error and **Retry connection**; Retry does not require username/password. Sign in discovers the selected service before using its advertised login action. Address edits clear the password; passwords are never persisted.

The browser pre-fills the hosting page’s origin. Entering a different HTTPS origin changes the action to **Open service**, disables credentials/account-link entry, and opens the service’s root-page Normal Bias app in the same tab. Each service must host its own web client. Browser API/audio remain same-origin; credentials are not transferred between pages.

Native clients save one HTTPS origin in ordinary local preferences after successful API discovery (before login); session credentials remain in origin-scoped secure storage. On launch the saved address takes precedence over the bundled trial address. Without either, enter the address on the sign-in page. An unsuccessful new address does not replace the saved preference or delete another service’s credential. Revisiting a service can restore its existing session. Account-link entry first discovers the address currently selected in the form.

Use `https://music.example.com`, `https://192.168.1.2:8443` or bracketed IPv6 such as `https://[2001:db8::1]:8443`. Whitespace, a trailing slash, hostname case and the default HTTPS port are normalised; paths, userinfo, queries/fragments and invalid ports are rejected. Certificates must be trusted and match the hostname; there is no TLS bypass. Runtime selection is separate from the unchanged private-address trial-build validation. Existing explicit development HTTP configuration remains usable only as its unchanged configured origin; this form does not enable arbitrary HTTP services or weaken the server’s loopback-only demo gate.

Focused browser acceptance: after preparing the disposable HTTPS fixture, run `RECORD_DEMO=1 node client/tests/auth-client/connection.cjs`. It covers inline failure/retry, login/restoration, validation, responsive layout and same-tab service navigation; the silent connection-only recording is written under ignored `client/target/recordings/service-connection/`. A separate synthetic second service is started and stopped by this check. See the authentication test README and verification record for exact setup and native evidence.

Hints use upright, regular-weight amber text (muted when disabled). All editable text boxes, including their hint text, use the same 3 mm internal inset in normal, focused and disabled states. This applies to connection/sign-in, account/password, account links and Library search.

## Graphite interface

The canonical [design reference](../DESIGN.md) governs all client UI. The retained player follows the supplied reference proportions: an edge-to-edge graphite player, artwork around 70% of its width, a compact inset metadata strip, thin white seek rail with elapsed/remaining times, a recessed transport wheel and four separate circular controls. The central wheel handles Previous, Play/Pause and Next. The upper-left outer control toggles Player information in place of artwork, changing from an information icon to a disc named Show artwork. A short horizontal slide swaps the two faces within the same bounds. The adjacent queue-symbol control opens Queue. The remaining outer controls are Restart track (lower left, switching to Retry track only on playback failure) and Shuffle queue (bottom right, replacing the former Library shortcut). Library and Profile controls live in the shared fixed toolbar outside the scrolling artwork/transport area; the form explicitly respects the platform safe area. Queue is a child screen of Now Playing, reached through the dedicated wheel Queue control; its Back action returns to the player. Queue’s toolbar exposes only Back, its title and Shuffle queue; Library and Profile remain on Now Playing. Library has no top-level Queue action. These controls remain reachable on short native screens even when the player body scrolls. All icon buttons retain accessible names; their actions are existing client capabilities.

The library uses continuous dark rows, circular artwork, a title/artist pair and one amber add-to-queue icon. Tap the title/artwork area to play; keyboard and accessibility names include the track title. A Search icon sits alongside the native text field; there is no search ×/Reset button. The All songs/Albums/Artists selector and a magnifier toggle share one Library row. Search is initially collapsed; Show search reveals the recessed field beneath that row and focuses the editor. Hide search commits editing and collapses the field while preserving entered text and the applied query/results. The magnifier stays amber while the field is open or a filter is applied; submit an empty query to clear the filter. Queue and Now Playing have no library search controls. Returning to Library preserves its current search and results. The search well shares the metadata panel’s dark fill, fine rim and rounded corners. All songs/Albums/Artists use the player’s moulded graphite pill edge, with amber text for the active view and white text for the others; the active colour persists when focus moves into search. Root views show their name only in the selector; inside an artist or album, contextual Back replaces the selector/search row, followed by the artist heading or album identity header. Search filters the catalogue; it does not filter or change the queue. Collections fetch automatically without pagination controls. On compact windows Library and Now Playing are separate top-level destinations. At widths of at least 130 density-aware mm and heights of at least 100 mm, the horizontal player console remains above Library or Queue. The shared toolbar switches to the other destination and exposes Profile for authenticated users; the Now Playing navigation mark is a rounded white play-triangle outline with a small amber centre. Profile opens a full-screen Account view with a read-only Username metadata well and the permitted Change password and Sign out actions. The well uses the player’s recessed metadata style, with a muted Username caption and bold wrapping authenticated username. It is account identity, not a role badge. Account has no popup or Close action. Its fixed Back control returns to the launching Library or Now Playing view, preserving playback and presentation state. Cancelling password change returns to Account, retaining that original Back destination. Account hides the mini-player while media continues. Compact Library and Queue retain the mini-player; its metadata opens Now Playing. The full player has no duplicate mini-player. The combined console retains the bounded wheel and scrolls its contents when necessary; short windows use compact navigation. Navigation preserves the Library query/group/scroll state and active media instead of reloading it.

Illustrations are deterministic original fallback sleeves selected by track title and shared by the player, metadata and list thumbnails; they remain fallbacks when server artwork is unavailable. Morning Circuit uses yellow and Evening Signal blue regardless of MP3 file hash. The upper-left wheel information button reveals read-only title, artist, album, duration and playback state in the artwork recess. Long values wrap and the panel scrolls independently. Empty queues show a choose-track message; details follow track changes and the selected face survives navigation. Swapping faces preserves position, play/pause intent and the same media resource. Normal playback does not display redundant status prose; loading and error/recovery messages remain visible. The seek rail has a larger interactive target and uses the slider event's captured value so polling cannot replace a user's requested position.

Both Shuffle queue controls perform the same one-shot shuffle of the entire current session queue. The currently selected track moves to its new queue index while continuing with the same media resource, playback position and play/pause intent. Previous/Next and completion use the new order. Shuffle does not automatically select or start a track when the queue has no selection. Empty/single-track queues disable both buttons. Randomness can retain the same order; this is an action, not a persistent shuffle mode. Queue membership and the 200-track bound are unchanged.

## Listening behaviour

- Choose **All songs**, **Albums** or **Artists** in Library. Albums opens its songs; Artists opens albums and then songs. Contextual Back steps out of a group. Switching category or opening a group clears search and starts at the beginning; Back restores the parent search/editor, search visibility and scroll anchor. Search is offered only at Library roots. Group rows show song counts and a representative track sleeve; absent artwork retains the generated fallback. Album groups use exact artist + album catalogue text, with Unknown labels for missing values. Compilation album-artist grouping and disc/track ordering are not yet supported.
- Browse/search with automatic 25-row fetches, bounded retention and loading, empty and failure states. Search follows the API's literal substring semantics. Late responses from earlier searches are ignored.
- Tapping a track adds/selects it in the session queue. `+ Queue` appends without interrupting playback; adding an existing track reuses its position. Queue contents survive searches and page changes, and are limited to 200 tracks. The queue is lost on page reload.
- Open Now Playing to use Play/Pause, the seek slider and Previous/Next. Its dedicated Queue control opens the Queue child screen, with artwork, track count, current-track indication and selectable songs. Queue Back returns to Now Playing; choosing a queued track or clearing stays on Queue. Use the Library toolbar icon to return to browsing. Select a queued track or use the player controls. Finishing a track advances to the next; finishing the queue stops. Shuffle queue randomises the current session order once; there is no persistent shuffle mode, repeat, persistent playlist or individual queue removal. Clear queue stops and releases the audio resource.
- The browser receives the existing API's MP3 stream with Range support. Seeking is available after the media duration becomes known. The player reports buffering, browser playback rejection and media failures. Restart track seeks the same current stream to zero without changing playing/paused intent. Empty queues disable it; restarting a completed track opens it paused at zero. Existing playback-grant renewal still applies when necessary. Only terminal playback failure changes the lower-left action to Retry track; loading disables the control and buffering alone leaves Restart. Retry reopens the failed stream using the last known position and intended playing/paused state; recovery restores Restart. Failure remains explicit player state until retry/new playback/clear, so a clean stale snapshot or adding queue entries cannot hide the recovery action. Failed tracks do not automatically skip or retry forever.
- Browser audio policy can require a user gesture; press Play if playback was blocked. Closing the page stops playback. Embedded ID3 covers now load asynchronously across library/player/mini-player; absent or unavailable art keeps the generated sleeve. Native background playback has a separate iPhone trial; iPhone lock-screen commands/metadata use a separate native bridge, while calls/audio focus and offline downloads remain outside the web increment.

## Continuous scrolling and alphabet navigation

Library automatically fetches 25-row batches as you approach either edge; there are no page numbers, range counts or pagination buttons. At most five batches (125 rows) are retained. Scrolling backwards reloads evicted rows with the visible anchor preserved. Rows outside the nearby viewport release decoded artwork and pending view listeners. Immutable artwork cache preparation runs outside active screen painting, retaining the existing sleeve palette/pattern and aspect-preserving cover crop; painting intersects ancestor scroll bounds and crops partial bitmaps so sleeves cannot draw over the fixed mini-player. The shared image cache remains bounded to 32 entries, with at most eight pending image keys; browsed resource metadata is capped at 150 entries plus the finite session queue's retained resources.

All songs sorts by title, Albums by album name, Artists by artist name, and an artist's albums by album name. Album tracks keep existing catalogue order. Alphabetical views show A–Z / # down the right side: tap or slide to jump, with an enlarged letter preview during touch. The first visible row determines the highlighted section. Letters also support keyboard activation and accessible names. Names keep articles; comparison ignores case and folds decomposable accents. Missing metadata, numeric/symbol and other-script initials use # after Z. Empty letters jump forward to the next populated section, falling back to the final populated section; empty results disable the rail.

Search retains the current group and indexes only the current filtered results. Returning from Now Playing/Account preserves the loaded window. Entering groups retains lightweight parent anchors; Back restores the parent's editor, query and position. Changing category or applying/clearing search starts at the beginning. Failed incremental loads retain existing rows with inline Retry. Browsing is live, not a frozen snapshot: concurrent edits/uploads/deletions can change offsets; a disappeared anchor falls back to the returned batch's beginning, and an out-of-range batch refreshes from the collection beginning.

This client increment requires the matching API's alphabetical discovery relations; older API consumers retain their existing contracts and default order. No whole-library preload, queue migration or new playback policy is introduced. See [DESIGN](../DESIGN.md) and the [API guide](local-poc.md#alphabetical-browsing-and-index-links-2026-11-24).

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

The Node/Playwright script is test orchestration, not application code or a client build tool. It launches the packaged API on a random loopback port with a new temporary data directory, generates 28 synthetic 12-second MP3s, and drives the actual generated client in headless Chromium. It checks empty library, search/clear input, automatic scrolling, playback progress, pause/resume, seek, queue navigation/completion, HTTP 206 delivery, forced audio/catalogue failures and recovery, queue clearing and resource release. It blocks service workers to exercise the freshly built assets. It saves a screenshot under `client/target/`; failed runs retain temporary API data/logs for diagnosis. It never opens an existing catalogue or personal music. Test processes stop afterward. Set `CI=1` only in a sandboxed CI environment that requires Chromium's `--no-sandbox` launch flag.

For a focused visual player check, run `node client/tests/player-button-states.cjs` after building the web bundle. It uses a disposable loopback catalogue and checks the wheel gradient and button corners at desktop, tablet and compact sizes, with paused preparation only. It also compares neutral/amber Queue glyph bounds through repeated open/close cycles in the larger layouts. Set `RECORD_DEMO=1` to capture the actual generated client; this presentation-only flow is silent.

Set `PLAYER_SIZE=desktop`, `tablet` or `compact` to start directly at one viewport and write evidence in that named subdirectory. This isolates fixed-size styling from live resize behaviour; it does not establish resize acceptance.

See [verification](verification.md) for the actual environment/results and remaining gaps. Chromium success does not establish Firefox/Safari support, device background audio, system media controls or CarPlay feasibility.

### Album and mini-player acceptance

After packaging the API and building the browser client as above:

```sh
node client/tests/album-header.cjs
```

This focused suite uses a separate loopback service and synthetic music. It verifies album headers from both navigation paths, Close/parent restoration, whole-album queueing across pages, duplicate/capacity policy, failed-page retry and cancellation, paused first-track preparation, immediate Play, Clear unloading, mini-player Next on Library/Queue, disabled queue-end controls, title pixels and compact/wide bounds. Capacity rejection is covered by the client JUnit suite; the browser suite exercises multi-page success/failure. It always records actual synthetic client audio/video under ignored `client/target/recordings/mini-next/` and stops its owned processes. Node/FFmpeg and Chrome are required; `CHROMIUM_PATH` overrides the default installed macOS Chrome executable. The recording is local evidence, not a public attachment; see verification for saved-file replay/audio checks and native acceptance.

### Large-library browser acceptance

After `mvn clean verify` and `./client/build-web.sh`, prepare the runtime classpath and run:

```sh
mvn -B -ntp package dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -Dmdep.includeScope=test -DskipTests
node client/tests/large-library.cjs
# Also export an actual-client/audio demonstration:
RECORD_DEMO=1 node client/tests/large-library.cjs
```

Set `CHROMIUM_PATH` to the local Chrome/Chromium executable when needed. `BrowseFixtureApplication` exists only on the test classpath and seeds a new disposable loopback catalogue with 14,000 synthetic metadata rows. Only the separately uploaded original synthetic musical clip has audio and is played; scale rows intentionally have no audio files. No external artwork lookup is enabled. The suite verifies bounded forward/reverse loading, direct letter jumps, touch sliding, filtering/fallback/empty results, group Back, stale-response rejection, failure/retry, keyboard access, short/wide layouts and media continuity. It blocks service workers, stops its owned processes, and saves evidence under ignored `client/target/recordings/library-scroll/`; failed fixture data are retained for diagnosis. The recording phase is a separate concise successful flow after the acceptance checks. Native boundary checks use `client/tests/prepare-large-library-ios.py` to prepare an ignored production-source copy with test-only MCP on 18888, then `client/tests/large-library-ios.py` against the isolated iPhone simulator app `dev.normalbias.scrollpreview`. `RECORD_NATIVE=1` records that visual-only native flow; the mini-player is compared against a genuinely empty-list screenshot, preventing equal overdraw in two populated views from producing a false pass. See verification for source-generation, signing/identity, TLS, service and actual run details.

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

The focused navigation check launches its own loopback demo-mode API, creates two original synthetic 90-second musical clips and verifies separate Library/Now Playing destinations, nested Queue/back, the dedicated Queue entry and inline information/artwork toggle, mini-player navigation, bounded wide player, resize continuity, the same media stream, preserved paused position/intent and clearing/release. It stops its API afterward. `RECORD_DEMO=1` saves an actual 390×844 browser/app-audio MP4 plus screenshots and state/timing evidence under ignored `client/target/recordings/compact-navigation/`. The capture helper preserves the initial viewport size; no replacement soundtrack is used. Browser smoke continues to cover search, grouping, automatic scrolling, seeking, completion and failure/retry. These demo suites block service workers and do not establish native reliability.

Account browser/native helpers open Profile before Sign out or Change password. Existing account-link and server authorisation contracts are unchanged. See [DESIGN.md](../DESIGN.md) for the saved concepts, their precedence and the requirement to retain the real circular player rather than the generated transport alternatives.

### Album contents header

Albums opened from either Albums or an artist's album list show the same compact cover/title header, with artist, song count and a vertically centred Queue album + at the right. The category/search row becomes contextual Back, restoring the parent query, editor, search visibility and scroll anchor. The title wraps; missing artwork uses the existing generated sleeve. Queue album adds all tracks in server catalogue order, skips existing queue entries and preserves existing playback. When the queue was empty, it selects and prepares the first track paused at 0:00 so you can press Play directly; it does not autoplay. It follows all contents pages, adds nothing on a failed/incomplete load or when the complete addition would exceed 200 tracks, and reports results/errors inline. Back, a Library context change or Clear queue cancels a pending load. Track rows, the fixed mini-player and current playback remain as before; album tracks do not show the alphabet rail. See [DESIGN](../DESIGN.md) for the approved concept and [verification](verification.md) for actual platform checks.

The album Queue and Library/Queue mini-player play/pause controls use the **Graphite hardware button** finish, reusing the player's restrained moulded component. The mini-player retains its existing playback behaviour; per-track queue pluses retain their unframed appearance.

### Ready after queueing; empty after clearing

The first single-track or album addition to an empty queue loads its first item into Now Playing and the mini-player, paused at zero. Once loading finishes, press Play to start that prepared track. Later additions preserve the current track and playback state. Clear queue unloads both prepared and playing audio, removes the current selection/artwork/metadata and resets timing; Play stays disabled until another track is queued or selected. Late preparation responses cannot restore a cleared track.

### Mini-player skip

The lower player bar on Library and Queue offers play/pause followed by Next track, using the same Graphite hardware finish. Next uses the full player's existing action and queue order, and is disabled when there is no next track. The title still opens Now Playing; its orange rectangular highlight is removed, with a thin white underline retained for keyboard focus. Both controls and metadata fit compact layouts, including narrow desktop/tablet windows. The combined layout shows only the full player.

### Library play and search controls — 2026-11-25

Tapping track artwork/title inserts a new track immediately after the current queue item and starts it. Next and automatic completion continue through the previously upcoming entries. Already queued tracks play from their existing position without duplication or reordering. With no selection, insertion is at the front; an empty queue contains only the chosen track, so Next is disabled until more tracks are added. The + button still appends (deduplicating existing entries) and preserves playback; first enqueue remains prepared paused. The queue cap remains 200; a rejected insertion preserves current media.

The search field has a Search action and no ×/Reset button. Use the category-row magnifier to show/hide it, preserving text/filter; edit and submit an empty query to clear filtering.

Run `node client/tests/library-play.cjs` after the browser build for focused queue insertion, existing-selection deduplication, + append/media continuity, Next/completion, search toggle/query/clearing and compact/wide layout acceptance. It records actual synthetic client audio/video under ignored `client/target/recordings/library-play/` using a disposable loopback API. The usual Chrome override, Java 21 and FFmpeg prerequisites apply.


## Larger-screen listening — top console and tiles

At available Form widths of at least **130 density-aware mm** and heights of at least **100 mm**, the shared Java client displays a horizontal top console with artwork/information, metadata/timeline and the unchanged wheel. Library fills the region below. The wheel Queue button opens/closes Queue in that region; Close restores Library's query/group/scroll. Queue retains count, Clear and Shuffle; Clear leaves Queue open and empty. Account remains full-screen with Back. There is no duplicate mini-player. Smaller windows retain the existing compact destinations and mini-player, including Queue Back to Now Playing.

Albums, Artists and artist albums use artwork tiles with wrapping names/metadata, targeting five landscape/four portrait columns on the reference tablet. Density adapts to width and text: 28 mm minimum covers, 3 mm gutters, at most five columns through 240 mm windows, with more allowed on wider desktops. All songs and album contents retain track lists and existing actions. Search, alphabet jumps and continuous loading still follow server links; five 25-item batches bound the loaded window. Grid reflow preserves the visible item's identity, and distant artwork is released. Resizing, rotation and Queue/Account navigation retain the live media resource, position, intent and selected information face.

The console scrolls when long metadata/status needs more height. Its wheel assembly is bounded at 60 mm and preserves its original geometry and four outer actions; compact PlayerFace remains unchanged. No API, queue policy or native media integration changes are required. This replaces the previously implemented three-pane concept C.

Build with `./client/build-web.sh`, then run `node client/tests/wide-console.cjs` (the older `wide-listening.cjs` entry point delegates to it). The suite uses a disposable literal-loopback demo API and original synthetic audio, checks responsive tiles and navigation/media continuity, and exports actual-client audio/video under ignored `client/target/recordings/wide-console/`. Set `CHROMIUM_PATH` to the installed browser when necessary. Existing compact suites now use an 800-pixel desktop viewport below the console threshold. Service workers are blocked for fresh-asset checks; this does not establish installed-PWA or physical-device support. See verification for actual results and native checks.


Native layout checks use `client/tests/prepare-console-ios.py` to prepare an ignored source copy/probe on port 18890, then the existing local iOS generation/Xcode route with the separate 19645 synthetic HTTPS fixture. `CONSOLE_ORIENTATION=portrait` or `landscape` selects assertions in `python3 client/tests/console-ios.py`; rotate the actual iPad with Device Hub first. The same isolated universal app supports `CONSOLE_SIM=<iPhone-id> CONSOLE_PHONE=1`; add `CONSOLE_PHONE_LANDSCAPE=1` after rotating the phone to check compact landscape Library. These helpers never replace retained previews. Exact native setup, results and limitations are in verification.

## Open an album from All songs

All songs rows now show an album/disc action before +. It opens the existing album header and complete contents, even when the song list has an active search. ‹ All songs restores the song list's query/editor, search visibility and scroll anchor. This is navigation only: current audio, position, play/pause intent and queue remain intact; + retains its append behaviour. Album contents, grouped lists and Queue do not repeat the shortcut.

The client follows the track's advertised GET `album` link, requires a single group with a contents affordance, and follows that link. No album URL or grouping rule is constructed in the client. Loading/error feedback is inline; retry uses the same icon. Changing category/view or disposing the form invalidates pending navigation. Services without the additive relation omit the icon while playback/+ continue working. The matching API is required for this new action; no migration is needed.

Run `node client/tests/track-album.cjs` after root API packaging and the browser build for compact/wide action bounds, complete album contents despite a filtered source list, query/search-visibility/scroll restoration, contextual navigation, actual audio continuity, failure/retry, stale cancellation and missing-link compatibility. It uses a disposable synthetic catalogue and records real client audio/video under ignored `client/target/recordings/track-album/`.

## Current manual simulator preview — 2026-11-26

The iPad Pro 11-inch (M5) and iPhone 17 Pro simulators each retain one **Normal Bias** app (`dev.normalbias.client`), built from the verified artist-queue source checkpoint with no test MCP server. They are signed in to the existing HTTPS library at **https://127.0.0.1:19544**, ready with playback stopped. Refreshed on 2026-11-26 with contextual Library Back navigation, artist-header option A and Queue artist; both are left on Oasis’s artist albums (‹ Artists), with empty session queues and playback stopped. The local HTTPS 19544 API was also updated with the artist tracks relation, preserving its configuration, TLS, accounts and music. The iPad is in landscape; the iPhone is in portrait. The older Wide, Preview, Scroll and Console copies were removed at Robin's request, with the earlier private ignored rollback backups under `client/target/simulator-refresh/`. The contextual-navigation refresh has its own rollback copies and build evidence under `client/target/contextual-simulator-refresh/`; the artist-header follow-up saves its app/data rollback copies and installed-binary checks under `client/target/artist-header-simulator/`. The artist-queue refresh saves app/data/service rollback copies and installed-binary evidence under `client/target/artist-queue-simulator/`. Native test-probe helpers above require their separate fixture app and do not operate this clean manual preview. See verification for the build/install/session/inventory checks; the latest artist-queue refresh includes the explicitly approved local API restart.

## Contextual Library navigation

The root category/search row is replaced inside artists/albums by a single graphite pill: ‹ Albums for albums opened from Albums, ‹ Artists for an artist's albums, ‹ artist name for albums opened through an artist, and ‹ All songs for the track-row album shortcut. No separate breadcrumb or Close × remains. Child views hide search; Back restores the parent's filter, editor text, search visibility and scroll anchor. Long artist labels truncate with their full accessible Back name retained. Queue/Account navigation and resizing preserve this state in compact and larger layouts. Album queueing and playback are unchanged.

After `./client/build-web.sh`, run `node client/tests/track-album.cjs` for focused contextual navigation and real-audio continuity evidence, `node client/tests/album-header.cjs` for album queueing/cancellation regression, and `node client/tests/browser-smoke.cjs` for the broader Library flow. Use Java 21 and the existing Chrome/FFmpeg prerequisites. The focused recording uses synthetic media and a disposable loopback demo API; it does not refresh the retained service or establish native-device/PWA support.

## Artist identity header

The artist's albums view shows a square representative library cover, larger bold wrapping artist name and muted total song count beneath ‹ Artists. It uses the same flat surface and lower seam as album headers, with a matching Queue artist + hardware action. Counts and artwork come from the selected server-advertised artist group; unavailable covers keep the generated sleeve and missing names show Unknown artist. Opening an album and returning restores this identity alongside the existing parent scroll state. Compact/larger layouts remeasure long names and retain the ordinary album list/grid and alphabet rail. Artist identity uses existing metadata/artwork; its queue action uses the additive API relation described below. No external artwork provider is involved.

Run `DEMO_NAME=artist-header node client/tests/track-album.cjs` after the browser build for artist-header placement, long/missing-name compact/wide checks, album/parent restoration and actual-client audio continuity recording under ignored `client/target/recordings/artist-header/`.


### Queue every track by an artist — 2026-11-26

Use the amber Queue artist + in the artist identity header to append all the artist's tracks across albums, skipping those already queued. It follows the server-advertised `tracks` and `next` links in catalogue order, independently of the visible album tiles. The same atomic loading, 200-track limit, inline feedback, cancellation and paused-first-track rules as Queue album apply. Existing playback is preserved. Album → artist Back restores the action. Older APIs without the artist `tracks` relation keep browsing available and disable Queue artist; update API and client together to enable it.

After `./client/build-web.sh`, run `node client/tests/artist-queue.cjs` with Java 21, installed Chrome and FFmpeg for the focused synthetic browser/audio checks and recording under `client/target/recordings/artist-queue/`.
