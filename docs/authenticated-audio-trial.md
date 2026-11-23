# Authenticated audio: native iOS simulator feasibility

Trial run 2026-11-23, at Robin's request. Robin selected Apple's iPhone simulator instead of the physical phone for this session. This is a bounded synthetic feasibility probe, not the account/login milestone or a public deployment.

## Result

Codename One **7.0.282 cannot attach stream headers on iOS**. Inspection of its packaged iOS/base bytecode shows the iOS implementation inherits `isStreamHeadersSupported() = false`; the native runtime confirms that a header-bearing `StreamRequest` is rejected. The ordinary audio adapter opens an AVPlayer URL without supplying API-request headers. Do not plan on the Java header API's existence establishing native support.

**Server-issued, track-scoped URL grants work in the native simulator with the existing player.** The test kept the real LibraryForm, Java Player/queue, FrameworkAudio and SystemAudio bridge. An isolated wrapper authenticated to a disposable HTTPS gate, followed its advertised grant/audio links and passed only the resulting limited audio URL to FrameworkAudio. The parent credential stayed in an Authorization header and Keychain, never in the audio URL. Each track open requested a new grant, including native completion-driven queue advance while Settings was foregrounded.

Observed results:

- Trusted loopback HTTPS, without disabling certificate validation.
- Native playback, immediate playing seek to 90 seconds, and paused seek to 10 seconds remaining paused. Seek commands used the existing Java Player methods through a development-only test tool; the checks do not establish touch-slider or lock-screen scrub acceptance.
- Real later HTTP 206 Range requests, including `bytes=5586944-5762124` after a late seek; not just playback of an initially buffered file.
- Settings foregrounded at 09:28:48.585 UTC; the next track's new grant issued at 09:28:53.785, followed by authenticated audio Range requests at 09:28:54.312/54.320. Native state subsequently reported queue index 1 and advancing playback, with actual captured output while off-screen.
- Non-prompting SecureStorage write/read and restoration after process relaunch passed **after ad hoc simulator signing**. The initial unsigned build failed its Keychain roundtrip. This is simulator evidence, not physical Keychain acceptance.
- Fifteen gate checks passed: anonymous catalogue/HEAD/Range denial, invalid/replayed pairing, authenticated catalogue access, valid/repeated ranges, wrong-track/mutation/catalogue scope denial, grant expiry and parent revocation.

**Lock limitation:** the native clock continued after the simulator Lock action and returning to the app preserved the second track/position. However, the saved audio contains an unexplained approximately 13-second silent interval during lock-screen interaction (117.245–130.231 seconds in the final clip). It does not establish uninterrupted locked audio; simulator output versus capture behaviour remains unresolved. No lock-screen media widget was visible in this trial. The account feature must retain a physical-device gate for locked playback, interruption handling and system controls.

## Isolation and test design

The tracked harness is under `client/tests/auth-audio/`. `prepare.py` creates a separate, ignored `client/target/auth-audio/client/` copy and makes guarded test-only injections there. It does not modify production application sources. The trial uses the same app identity **only in the simulator**, without creating/installing another physical-phone app. The retained API/library and personal MP3s are untouched.

The actual Spring API runs on loopback port **18444** with a disposable data directory and artwork-provider jobs disabled. A Java JDK HttpsServer test gate on **18443** forwards read-only catalogue/audio traffic to that API. This is fixture infrastructure, not a replacement backend or production security implementation. Its parent session is ephemeral in server memory, grants are stored by hash and expire after two minutes, and every new audio request checks grant scope/expiry plus parent validity. Existing responses may finish. A loopback-only control listener on **18445** supports inspection/revocation for the gate tests.

The gate advertises grant/audio action links and the wrapper follows them. The rest of the existing catalogue contract is unchanged; this probe does not implement the broader HATEOAS migration. Pairing uses a disposable fixture secret in the ignored copied app resource, never real account credentials. Do not ship that resource, probe class, MCP endpoint or fixture authentication in a production app. The only native test endpoint is loopback port **18867** inside the simulator, which shares the Mac's loopback network.

Audio is two generated three-minute MP3 clips. The gate throttles audio transfer so seeking can require a fresh request. TLS uses a two-day disposable certificate trusted only inside the selected simulator, not the Mac or physical phone. This run's certificate expires **2026-11-25 09:14:12 UTC**. No global TLS bypass, cloud build, dependency upgrade, signing-account change or public listener is involved.

## Reproduce

Requires macOS/Xcode with an installed iOS simulator, Java 21, Maven, Python 3, OpenSSL and FFmpeg. Select Java 21 first. The actual run used SDKMAN Temurin 21.0.9, Xcode/iOS SDK 27.2, and an iPhone 17 Pro simulator running iOS 26.0, UUID `78BD8653-8300-4EC1-A2C8-84AC2503352A`.

From the repository root, using a fresh ignored trial directory:

```sh
mvn -B -ntp verify
python3 client/tests/auth-audio/prepare.py
javac -d client/target/auth-audio/classes client/tests/auth-audio/TrialServer.java
```

`prepare.py` refuses to overwrite an existing run. Preserve evidence or explicitly move that task-owned output aside before preparing another run. In separate terminals, start the disposable API, upload the generated clips, then start the TLS gate:

```sh
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar \
  --server.port=18444 --normalbias.data-dir="$PWD/client/target/auth-audio/data" \
  --normalbias.auth.mode=demo --normalbias.artwork.background-enabled=false
java -jar cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar \
  client/target/auth-audio/fixtures http://127.0.0.1:18444
java -cp client/target/auth-audio/classes TrialServer client/target/auth-audio
```

Generate only the copied client, then build/sign for the selected simulator:

```sh
mvn -f client/target/auth-audio/client/pom.xml -B -ntp package \
  -Dcodename1.platform=ios -Dcodename1.buildTarget=ios-source -Dopen=false \
  -Dcodename1.arg.ios.deployment_target=17.0 -Dcodename1.arg.ios.carplay.audio=false
xcodebuild \
  -project client/target/auth-audio/client/common/target/client-common-0.1.0-SNAPSHOT-ios-source/NormalBias.xcodeproj \
  -scheme NormalBias -configuration Debug -sdk iphonesimulator \
  -destination id=78BD8653-8300-4EC1-A2C8-84AC2503352A \
  -derivedDataPath client/target/auth-audio/DerivedData \
  CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=- -jobs 4 build
xcrun simctl boot 78BD8653-8300-4EC1-A2C8-84AC2503352A
xcrun simctl keychain 78BD8653-8300-4EC1-A2C8-84AC2503352A add-root-cert client/target/auth-audio/tls.crt
xcrun simctl install 78BD8653-8300-4EC1-A2C8-84AC2503352A \
  client/target/auth-audio/DerivedData/Build/Products/Debug-iphonesimulator/NormalBias.app
xcrun simctl launch 78BD8653-8300-4EC1-A2C8-84AC2503352A dev.normalbias.client
```

Skip boot if the selected simulator is already booted. Do not run this simulator build/install procedure against a physical device. The reusable native tool supports `snapshot`, `headers`, `pause`, `resume`, `seek90`, `seek10`, `nearEnd`, `next` and `clear`:

```sh
java .agent-skills/codename-one/tools/Sim.java --port 18867 \
  call auth_audio_probe '{"op":"snapshot"}'
```

Use the real app to select Morning Circuit, add Evening Signal and open Now Playing. Verify playing/paused seeks using the tool; `nearEnd` seeks to 175 seconds. Immediately launching simulator Settings with `xcrun simctl launch ... com.apple.Preferences` backgrounds the player before natural completion. Verify the server issued the next grant after that transition, and that playback then advances on queue index 1. Do not poll the player to cause completion before checking the server's native-callback evidence.

For standalone gate tests, use a fresh server process with `grant-ms=1000` added to its ignored `server.properties`, then run `python3 client/tests/auth-audio/check-gate.py client/target/auth-audio`. It consumes pairing and revokes the parent session; stop that gate and remove the override before a fresh native run. Restarting the gate invalidates its ephemeral credentials, so an app retaining an old trial credential needs its trial app data/credential cleared or a fresh simulator installation context. This is not production session persistence.

## Recording and acceptance limits

The final ignored artifact is `client/target/recordings/auth-audio-ios-final/demo.mp4`: a continuous native-screen recording with the actual simulator process's captured audio, not a replacement soundtrack. `capture-audio.swift` uses a private unmuted Core Audio process tap; `record.py` pairs it with `simctl io recordVideo`. Identify the current NormalBias Core Audio process object before recording; **162 was specific to this run**, not a reusable ID. Supply a fresh recording directory, then touch its `stop-recording` file to finalize. Recorder start notifications share the host clock; the mux trims this run's **0.415-second** audio lead. Alignment is approximate, not sample-accurate.

`summarize.py` checks native telemetry and server evidence using the recording's `timing.json` and `background.json`; its eight assertions passed. Save the gate's native-run `events.jsonl` as `native-events.jsonl` in the recording directory after the relaunch check and before resetting the gate; the summarizer prefers that snapshot so later standalone tests cannot replace native evidence. Saved Chrome playback, decoded audio and exported frames were checked; exact artifact measurements, commands and the lock-screen silence gap are in [verification](verification.md). Audio was measured rather than monitored by ear. No physical-device, prolonged-background, call/headphone interruption, native URL-grant renewal after expiry, production account policy or browser authentication acceptance is claimed.

For implementation, use server-issued, revocable, narrowly scoped media links as the proven simulator route. Design grant renewal for long pauses/expiry so reopening media can preserve the requested position and paused/playing intent. Grant caching, renewal, full account/session persistence and production authorisation remain part of the authentication milestone. The physical device and uninterrupted locked-audio gates remain open.

## Later production-client simulator acceptance — 2026-11-23

The account-flow follow-up tests the integrated production Java client with real H2-backed sessions, remembered Keychain credentials and server-issued URL grants. Robin explicitly selected the simulator instead of physical iPhone interaction. Account forms, real grant expiry, background completion, real API outage/restart and operator revocation pass. The final ignored recording is `client/target/auth-client/locked-queue-final/demo.mp4` (93.97 seconds): lock is verified before track completion, the native callback obtains the next track, and the screen stays locked. Captured app audio contains a 0.70-second transition pause at 38.88–39.59 seconds, with no recurrence of the historical 13-second silence. The old cause remains unknown; this bounded result does not establish physical-device, phone-call/headphone or prolonged background reliability. Exact commands, export details and saved-artifact checks are in verification.md. Historical gate results above remain historical.
