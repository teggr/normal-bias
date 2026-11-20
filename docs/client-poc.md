# Web listening-client PoC

The Codename One client lives in `client/`, with a separate Maven reactor and Java 17 source compiled using a Java 21 JDK. The API and uploader remain Java 21. Building the API does not build the client. Codename One 7.0.282 is pinned in the client POM.

## Build and run locally

Requires Java 21 JDK, Maven 3.6.3+, Bash, `unzip` and internet access to Maven Central, `repo.codenameone.com` and Codename One's official tool downloads. The first build installs vendor tools under `~/.codenameone`; this cache must be writable. No Codename One account, cloud build credits or paid plan is needed for the local JavaScript target used here.

From the repository root:

```sh
mvn clean verify
./client/build-web.sh
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar \
  --spring.web.resources.static-locations="file:$(pwd)/client/target/web/"
```

Open `http://127.0.0.1:8080/`. In another terminal, upload the bundled synthetic fixture to start:

```sh
java -jar cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar api/src/test/resources
```

The web build script runs the client JUnit tests and compatibility check, translates Java to JavaScript locally, and replaces only the generated `client/target/web/` directory with the extracted static bundle. All generated output is ignored by Git. The API's static-resource setting is a launch-time option; its default startup behaviour and existing endpoints are unchanged. Keep the trailing slash in the static-resource location. Do not serve the repository root as the web root.

To keep the demo separate from an existing library, add `--normalbias.data-dir=/absolute/path/to/disposable-data` to the API command. To avoid a port conflict, add `--server.port=8090`, open that port in the browser and pass `http://127.0.0.1:8090` as the CLI's second argument. Keep the API's loopback binding. This is still an unauthenticated local PoC.

The client derives the API origin from the browser page URL, so catalogue and audio requests go straight to `/api/tracks` and `/api/tracks/{id}/audio` on the same server. It does not need a CORS proxy or CORS permissions. Opening the extracted HTML with `file://`, or serving it on an unrelated origin, is not supported by this increment.

Codename One generates a PWA app-shell cache. After rebuilding, close existing tabs for the app and reopen it so a waiting worker can activate. If an earlier build is still shown, clear that local site's data or use a fresh browser profile. App-shell caching is not offline music support; the catalogue and audio need the API.

## Graphite interface

The [Graphite design](design-system-proposal.md) now follows the supplied reference proportions: an edge-to-edge graphite player, artwork around 70% of its width, a compact inset metadata strip, thin white seek rail with elapsed/remaining times, a recessed transport wheel and four separate circular controls. The central wheel handles Previous, Play/Pause and Next. Outer controls open track details, Queue, Retry track and Library. Back and information controls live in the player header. All icon buttons retain accessible names; their actions are existing client capabilities.

The library uses continuous dark rows, circular artwork, a title/artist pair and one amber add-to-queue icon. Tap the title/artwork area to play; keyboard and accessibility names include the track title. Search and reset use icons alongside the native text field. Pagination controls appear when needed. Wide windows keep the player beside the library, with its width limited by available height. Compact windows have a mini-player and a dedicated Now Playing view that hides the browsing header/tabs; Back and Library/Queue controls restore navigation. Short viewports retain scrolling.

Illustrations are deterministic original fallback sleeves shared by the player, metadata and list thumbnails; the API still has no cover-art field. Full title/album/status information is available from the player information/details buttons. Normal playback does not display redundant status prose; loading and error/recovery messages remain visible. The seek rail has a larger interactive target and uses the slider event's captured value so polling cannot replace a user's requested position.

## Listening behaviour

- Browse/search 25 tracks at a time, with Back/More, exact matching totals and loading, empty and failure states. Search follows the API's literal substring semantics. Late responses from earlier searches are ignored.
- Tapping a track adds/selects it in the session queue. `+ Queue` appends without interrupting playback; adding an existing track reuses its position. Queue contents survive searches and page changes, and are limited to 200 tracks. The queue is lost on page reload.
- Use Play/Pause, the seek slider, Previous/Next, or select a queued track. Finishing a track advances to the next; finishing the queue stops. There is no shuffle, repeat, persistent playlist or individual queue removal yet. Clear queue stops and releases the audio resource.
- The browser receives the existing API's MP3 stream with Range support. Seeking is available after the media duration becomes known. The player reports buffering, browser playback rejection and media failures. Retry track reopens the stream from the beginning; it remains available during loading. Failed tracks do not automatically skip or retry forever.
- Browser audio policy can require a user gesture; press Play if playback was blocked. Closing the page stops playback. Native background/lock-screen/headphone controls, artwork, calls/audio focus, offline downloads and mobile support are not delivered by this web increment.

## Browser audio integration

UI, catalogue access, queue and playback policy are Java. A small JavaScript native-interface implementation (`client/javascript/src/main/javascript/dev_normalbias_client_player_BrowserAudio.js`) calls the browser's `HTMLAudioElement` on the main thread and returns an atomic state snapshot to Java every 500 ms. It checks that stream URLs belong to this origin and match the audio endpoint, closes old media before opening a new track, and ignores stale events and play promises.

This platform glue is a workaround for an observed Codename One 7.0.282 JavaScript-port failure: `MediaManager.createMediaAsync()` successfully loads the stream, but its HTML5 media wrapper then throws `TypeError: el.play is not a function` against a worker-side host proxy. The PoC therefore does **not** validate Codename One's built-in media adapter. Recheck that adapter on a future framework update before removing the bridge. The native-interface source and generated bundle were verified locally. No Android/iOS implementation of this bridge is supplied; those platforms need their own media integration milestone, using `createBackgroundMedia()`, remote commands and metadata where suitable.

The existing API needed no contract, storage or security changes. Artwork extraction/serving and authenticated remote access remain future API work.

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

The authorised simulator increment now provides local playback through the framework media API and compact phone-screen layouts. Use `./client/demo-simulator.sh` for a disposable synthetic library, or `./client/run-simulator.sh` for an existing loopback API. See the [simulator guide](simulator-poc.md) for prerequisites, audio/proxy setup, tested commands and limits. This does not add Android/iOS implementations of BrowserAudio or establish native background playback.
