# Try Normal Bias

The quickest introduction is the local browser demo. It builds the real application and supplies two original synthetic musical clips, Evening Signal and Morning Circuit, with geometric covers. No personal music, account or cloud build subscription is required.

## Prerequisites

- Java **21 JDK** and Maven **3.6.3+**. Both `java -version` and `mvn -version` must report Java 21; set `JAVA_HOME` and `PATH` to the same JDK.
- Bash, Python 3, unzip and curl.
- FFmpeg with MP3 encoding and FFprobe, available on `PATH`.
- A current desktop browser, plus internet access to Maven Central, `repo.codenameone.com` and the official Codename One tool downloads for the first build.
- A writable checkout, temporary directory, Maven cache and `~/.codenameone` tool cache.

There is no Maven wrapper. FFmpeg is needed to generate the demo music; it is not required for the API's ordinary build/tests. Native Apple tooling is unnecessary for this browser demo.

## Start and listen

From the repository root:

```sh
./client/demo-web.sh
```

The first build may take several minutes. The helper runs the root Maven verification and `client/build-web.sh`, which tests and translates the separate Java client reactor. Once it prints **Normal Bias is ready**, open `http://127.0.0.1:18080/`.

1. Select a song's title/artwork to start playback.
2. Browse Albums, open Simulator Sessions, and use Queue album + to append both tracks. Adding to an empty queue prepares the first track paused; press Play to listen.
3. Open Queue using the player's Queue control. Try pause/resume, Next, or Shuffle queue.

The larger browser view keeps the player above Library/Queue; compact windows have a separate Now Playing destination and mini-player. Music never starts automatically on page load.

Keep the terminal running. Ctrl-C stops the helper's API and removes the temporary music/catalogue. Builds and vendor caches remain. Starting it again creates a fresh two-track library. Do not import music you want to retain into this disposable service.

For another port:

```sh
NORMALBIAS_DEMO_PORT=18081 ./client/demo-web.sh
```

The value must be 1024–65535. Open the URL printed by that invocation. The helper checks availability before building, binds only to `127.0.0.1`, waits for its own API startup before uploading, and disables automatic external artwork lookup. It does not stop or connect to another running service. Demo mode cannot be used as a phone or public-network demo.

### Try your own music temporarily

```sh
NORMALBIAS_DEMO_MUSIC_DIR="/absolute/path/to/music" ./client/demo-web.sh
```

The directory must contain regular MP3 files and must not itself be a symlink. The normal uploader recursively imports MP3s through the API, skipping symlinks and deduplicating identical files. It reads your source files without changing them; imported copies live in the helper's temporary library and are deleted on exit. Allow enough temporary disk space for those copies. Original embedded covers are used where available; external artwork lookup remains disabled. Omit the variable to return to the two synthetic tracks. All listed prerequisites still apply.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| Missing program or wrong Java version | Install the listed prerequisite; check `JAVA_HOME`, `PATH`, `java -version` and `mvn -version`. |
| Port unavailable | Choose another `NORMALBIAS_DEMO_PORT`; leave other services running. |
| Maven/vendor download fails | Check network access and writable tool caches; rerun the helper. |
| Old UI after rebuilding | Close the app's existing tabs and reopen it. If necessary, clear only this local demo site's data or use a fresh browser profile. |
| Browser cannot connect | Wait for the ready message, use its exact URL and keep the terminal open. If the helper fails, inspect its terminal output. |
| No sound | Select a track or press Play, check browser/site mute and system output. The generated clips fade in; visual progress alone does not establish audible output. |

Do not open generated HTML with `file://` or serve the repository root. The helper serves the generated browser bundle from the same origin as the API.

## Screenshots and walkthrough

The README gallery shows the actual generated desktop browser client, native iPhone/iPad simulator builds, and Codename One's desktop CarPlay head-unit renderer. At Robin's explicit request, these captures and the walkthrough use the 24 locally supplied Uploads tracks (Oasis and Roxy Music), imported into disposable services. Original MP3s, imported files and catalogues remain outside Git; screenshots contain the app's displayed metadata/artwork. A fresh clone's default demo instead generates its own synthetic clips/covers. Simulator captures demonstrate their respective renderers, not physical-device or real-car acceptance. CarPlay remains behind its native entitlement/signing gate.

The browser walkthrough shows album browsing, paused album preparation, playback, Queue, pause/resume and Next, with the client's actual sound. Its MP4 and intermediate files belong under ignored `client/target/recordings/repo-demo/`; they are delivered separately, not committed or published to a public host. The README's static gallery works without the video.

To reproduce the desktop screenshot and walkthrough after starting a fresh demo:

```sh
npm ci --prefix client/tests
node client/tests/repo-demo.cjs
```

The capture uses Playwright with installed macOS Chrome by default; set `CHROMIUM_PATH` for another Chrome/Chromium executable. For a custom port, set `NORMALBIAS_DEMO_ORIGIN=http://127.0.0.1:18081`. By default it requires the two expected synthetic tracks. To capture your explicitly selected local music, pass the same `NORMALBIAS_DEMO_MUSIC_DIR` to both the launcher and capture command; the capture checks that the service contains exactly those MP3 hashes (at most 200 source files). It prefers the Roxy Music album when present, otherwise the first album. It drives actual canvas pointer input, enables the normal service worker in a fresh browser context, and records actual browser media output using the existing capture helper. It updates `docs/screenshots/desktop.png` and writes the MP4/flow timing evidence to ignored output.

Native capture uses an isolated production-source copy with the existing test-only MCP probe, separate iOS simulator devices and a disposable HTTPS fixture. See the dated [verification record](verification.md) for exact commands and sources. For the CarPlay renderer, use the [desktop probe instructions](carplay-poc.md#desktop-test); do not substitute concept art or describe it as Apple's native simulator.

For a persistent library, move on to [authentication and administration](authentication.md), the [API/uploader guide](local-poc.md) and the [client build guide](client-poc.md).
