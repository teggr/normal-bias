# Desktop simulator PoC

The Codename One Java client can now run in its desktop simulator with a phone skin, a local API and streamed audio. This is the desktop JVM simulator, not an Android emulator or the Apple iOS simulator; it does not establish native device, background playback or CarPlay support.

The desktop helper intentionally selects loopback-only `normalbias.auth.mode=demo`. The shared client supports login and scoped playback URLs in the web/iPhone HTTPS flow, but `run-simulator.sh` still accepts only loopback HTTP origins, so an API supplied to that wrapper must use demo mode. See [authentication](authentication.md) for the authenticated web/iPhone route.

The separately authorised [CarPlay PoC](carplay-poc.md) exercises the framework's simulated head unit against the same Java player. Its guide covers the Car menu, repeatable desktop probe, local iOS preparation and the Apple signing gate.

## Run the disposable demonstration

Install a Java 21 JDK, Maven 3.6.3+, Bash, FFmpeg (`ffmpeg` and `ffprobe`) and `curl`, all on PATH. Use a graphical desktop with a working Java Sound output device. No Codename One cloud account or signing assets are required.

From the repository root:

```sh
./client/demo-simulator.sh
```

The helper builds/verifies the API and uploader, generates two original 42-second synthetic audio clips, launches a loopback API on port 18080 with a new temporary H2 catalogue, uploads the clips and starts the simulator. It waits for its own API's startup log before uploading, so an occupied port fails rather than using an existing library. Override the port with `NORMALBIAS_DEMO_PORT=18081`. Closing the simulator or cancelling the helper stops its API and removes its temporary music/catalogue. It never opens the normal `./data` directory. Generated simulator build/tool caches follow the normal Maven/Codename One locations.

Use the magnifier beside the Library categories to reveal search, then search for “Morning”, play Morning Circuit, pause/resume, seek, search for “Evening” during playback, add Evening Signal to the queue and use Next/Previous. The player Queue button opens a child screen showing both tracks; Clear queue stops/releases playback. Finishing a track advances through the finite queue. The helper's audio clips are synthetic chords, not personal music.

The complete helper was exercised on port 18081: backend verification, synthetic generation/upload, client verification and visible simulator library startup passed. After terminating its simulator, the helper stopped its API. Bash syntax and invalid-port guards also passed. See the dated verification record for the exact environment and launch.

## Connect to an already running local API

```sh
# Use the default local API at 127.0.0.1:8080:
./client/run-simulator.sh
# Or another local port:
NORMALBIAS_API_ORIGIN=http://127.0.0.1:8090 ./client/run-simulator.sh
```

This wrapper calls `mvn -f client/pom.xml -pl common cn1:run`. The pinned 7.0.282 plugin supports the simulator without a separate `javase/pom.xml`. It lacks the newer `cn1:agent` goal. The wrapper forwards the loopback origin to the forked simulator JVM via `JAVA_TOOL_OPTIONS`; a non-loopback origin is rejected. It also excludes loopback from FFmpeg's environment proxy settings. The browser client continues deriving its API origin from the web page.

If catalogue requests return a proxy-generated 403, use the simulator's **Tools → Network → Proxy Settings → No Proxy** for this local development session, then restart the simulator JVM. The simulator can initialise Java's system proxy independently of Maven's settings. Do not change the API binding or deploy it publicly to solve this.

## Playback and layout

The shared `FrameworkAudio` adapter uses `MediaManager.createMediaAsync()` and framework media state/completion/error handling, while the web target retains the existing browser bridge. Audio creation runs off the EDT; stale completions are discarded and their resources released after queue changes/clear. A media load/start that stays pending for 15 seconds becomes a retryable failure. This does not guarantee recovery from every output-device failure: the framework's FFmpeg worker can log a missing Java Sound line without publishing a media error.

The player keeps the existing finite session queue and controls in the shared Graphite theme: inset display, dark transport wheel and spacious library rows. Library and Now Playing are separate destinations at every width, with a shared compact toolbar. Library and the nested Queue screen retain the mini-player; wide layouts centre the bounded existing player instead of showing a sidebar. Now Playing retains the existing wheel and four outer buttons beneath the fixed toolbar; short screens scroll its body. The dedicated Queue control opens that child view; the bottom-right former Library control now shuffles the session queue. Queue’s toolbar offers only Back to Now Playing and Shuffle queue. Shuffling retains the current track, position and play/pause intent; fewer than two tracks disables it. The lower-left circular-arrow control restarts the current track at 0:00 while keeping playing/paused intent; a playback failure changes it to Retry until recovery. Loading disables this action, and completed tracks restart paused. The former (…) control is now an information/disc toggle that slides current-track details into the artwork area and restores artwork; it opens no popup. The Now Playing toolbar contains Library and Profile, with no information action. Native fonts and framework input remain; this is not a UIKit or Compose implementation. Original abstract sleeves remain the fallback when server artwork is unavailable. See the [client guide](client-poc.md#graphite-interface) and dated verification record for tested widths and remaining gaps.

Network failures use the existing inline failure/retry states. The local PoC disables the framework lifecycle's automatic cloud crash-report binding and consumes the generic lifecycle network error, avoiding duplicate dialogs or build-account prompts. This is not a cloud reporting integration.

## Optional agent inspection and recording

To enable the framework's development MCP socket on loopback:

```sh
JAVA_TOOL_OPTIONS='-Dcn1.mcp.port=18765' ./client/run-simulator.sh
# In another terminal, from the repository root:
java .agent-skills/codename-one/tools/Sim.java --port 18765 snapshot
```

No MCP connection is added to the user's account. The simulator's built-in recorder supports `audio=app`; apply the [recording skill](../.agents/skills/ui-demo-recording/SKILL.md). Keep generated recordings under `client/target/recordings/`.

The managed Linux desktop used for verification had no physical sound output. A task-local PulseAudio software sink plus ALSA plugin supplied a real-time Java Sound output; the framework recorder captured actual decoded application PCM through its audio tap. This machine setup is not an application dependency or a claim of speaker/headphone output. On a developer machine, use its normal sound output. A missing output device is a concrete playback blocker, not evidence of successful sound playback.

See [verification.md](verification.md) for test results, recording checks and outstanding device work.

The disposable synthetic demo helper explicitly disables automatic external artwork lookup (`--normalbias.artwork.background-enabled=false`). Ordinary retained-library service launches enable it by default; the mocked-provider browser fixture tests automatic filling independently.
