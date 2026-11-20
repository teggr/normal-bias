# Local device trial plan

For the separately authorised 2026-11-20 CarPlay feasibility trial, use the [CarPlay guide](carplay-poc.md). Desktop head-unit verification and unsigned native compilation do not establish entitled-device or real-unit CarPlay support; the current signing profile rejects the audio entitlement.

Proposed 2026-11-20 against `d137316`. This is a trial plan, not a verified native-build guide. The selected target is an iPhone 15 Plus running iOS 27.2.1 (selected 2026-11-20). Keep Java 21, Maven and Codename One 7.0.282; do not upgrade the framework just to follow newer examples.

Goal: build on your own computer, install on one physical phone, and browse and play a disposable synthetic library without deploying the service. The original foreground trial is complete. The 2026-11-20 follow-up retains audio when switching apps or locking the iPhone, and adds embedded covers; the acceptance checklist below records the remaining physical checks. CarPlay remains separate.

## 1. Establish the working local baseline

Use a clean checkout of current `main`. Install Java 21 JDK, Maven 3.6.3+, Bash, FFmpeg/FFprobe, curl and unzip. Check `java -version` and `mvn -version` both use Java 21. On a Mac with several JDKs, select it with:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH="$JAVA_HOME/bin:$PATH"
```

From the repository root:

```sh
mvn clean verify
./client/demo-simulator.sh
```

The simulator helper prepares a disposable synthetic library. Check browsing, playback, pause/resume, seeking and queue advance. Stop the demo before moving on. See [simulator instructions](simulator-poc.md) for sound and proxy troubleshooting. These are existing project commands; this planning task has not rerun them on your computer.

Exit criterion: the existing app works locally before introducing native packaging.

## 2. Prove native packaging before implementing playback

Choose one path:

| Target | Local prerequisites | First result |
| --- | --- | --- |
| Android | Android Studio/SDK, platform tools, USB cable, developer options and USB debugging | Installable debug APK; no Play Store account needed |
| iPhone | Mac, Xcode and command-line tools, connected/trusted iPhone with Developer Mode where required, Apple signing team | App launched on the phone from Xcode |

For iPhone, try Xcode's Personal Team for personal testing if supported by the generated project; provisioning, expiry and entitlement limits apply. Do not assume a paid membership is required for the initial foreground test. Resolve any project-specific signing requirement from the actual Xcode result.

First inspect the pinned plugin's build goals from the repository root:

```sh
mvn -f client/pom.xml help:describe \
  -Dplugin=com.codenameone:codenameone-maven-plugin:7.0.282 \
  -Ddetail
```

The vendored upstream reference describes `android-source` for an Android Studio project and `ios-source` for an Xcode project. Validate their handling and host-module requirements against **7.0.282** before running a source build. Current platform POM profiles alone do not establish a working device build. Keep generated native projects under ignored build output.

Candidate commands to validate, run from the repository root, are:

```sh
# Android source-project generation — NOT YET VERIFIED for this project
mvn -f client/pom.xml package -Dcodename1.platform=android \
  -Dcodename1.buildTarget=android-source -Dopen=false

# iPhone source-project generation — Mac only; NOT YET VERIFIED for this project
mvn -f client/pom.xml package -Dcodename1.platform=ios \
  -Dcodename1.buildTarget=ios-source -Dopen=false
```

If generation succeeds, use the logged output path. Build/run the Android debug variant in Android Studio, or select the signing team and connected iPhone in Xcode and Run. A generated project's Gradle tooling is platform build output; the application source/build remains Java/Maven.

If the target is rejected, record the exact error and investigate the pinned plugin's local builder. Do not silently substitute `android-device`/`ios-device`: those normally submit cloud builds. A Maven success without a generated native project and installed app does not pass this step.

Exit criterion: the app launches on the selected physical phone. An unsupported-audio message is expected until step 4. If local packaging is blocked, resolve it before spending time on playback integration.

## 3. Give the phone a deliberate development API connection

Prepare a separate loopback API with a disposable data directory and upload the committed synthetic fixture using the existing [local PoC instructions](local-poc.md). Do not use a personal music directory. The upload CLI remains loopback-only.

- **Android preferred route:** connect over USB, check `adb devices`, then run `adb reverse tcp:8080 tcp:8080`. The Android client can reach the computer's loopback API at `http://127.0.0.1:8080/` through that forwarding rule. Permit HTTP only as needed for the debug variant. Remove forwarding afterwards with `adb reverse --remove tcp:8080`.
- **iPhone route:** use the Mac and phone on a trusted private network. Add an explicit native development API-origin setting, then deliberately launch the disposable API bound to the Mac's private interface and point the app at that address. Configure any required iOS local-network permission and trusted HTTPS or narrowly scoped debug HTTP exception. Keep the normal API binding at loopback; do not port-forward or publish it. Restore loopback-only operation after the test.

The simulator launcher override remains simulator-only. For the iPhone development build, the new helper bundles a separate explicit origin and creates a host-specific HTTP exception; it does not change the browser's same-origin behavior or the API's normal loopback binding. A phone's loopback address normally means the phone itself, except when explicit USB forwarding is active.

Implemented local iPhone trial commands (Java 21/Maven/FFmpeg, iOS 17+):

```sh
# Replace the address with your Mac's actual private IPv4 address.
export NORMALBIAS_API_ORIGIN=http://192.168.1.205:18082
mvn clean verify
./client/demo-device-api.sh
# In another terminal, with the same explicit origin:
./client/build-ios-trial.sh
```

The API helper creates two new 42-second synthetic tracks and a temporary library, uploads using the loopback-only CLI, stops that service, then restarts the same disposable library bound only to the chosen private interface. Ctrl-C (or the printed `kill -TERM <helper PID>` command) stops the owned API and removes its temporary files. It verifies its own startup before uploading. Do not use an occupied port or expose the endpoint through a public tunnel/router forwarding rule. Check the catalogue URL from Safari on the phone before proceeding.

The build helper fixes the Maven target to **local `ios-source`**, requires Java 21, and rejects public/loopback/wildcard hosts, credentials, paths and privileged ports. It uses the opt-in `native-trial` Maven profile to bundle `normalbias-trial-origin.txt`; the template contains no machine address. It removes that resource from shared target/classes after generation so an ordinary subsequent browser/simulator package does not retain the address. Do not invoke the profile directly with unchecked values. The generated project has iOS minimum 17, a single-IP `NSExceptionDomains` HTTP exception, `NSAllowsArbitraryLoads=false`, and a Local Network usage explanation. No global HTTP/TLS bypass is added. This is a deliberate private development connection, not authenticated remote hosting.

Generate first, then build/sign the project. Regeneration replaces generated signing settings; pass your local team ID to Xcode (it is not a private key):

```sh
xcodebuild \
  -project client/common/target/client-common-0.1.0-SNAPSHOT-ios-source/NormalBias.xcodeproj \
  -scheme NormalBias -configuration Debug -sdk iphoneos \
  -destination "id=$IPHONE_UDID" \
  -derivedDataPath client/target/device-trial/DerivedData \
  DEVELOPMENT_TEAM="$APPLE_TEAM_ID" CODE_SIGN_STYLE=Automatic \
  -allowProvisioningUpdates -allowProvisioningDeviceRegistration -jobs 4 build
xcrun devicectl device install app --device "$IPHONE_UDID" \
  client/target/device-trial/DerivedData/Build/Products/Debug-iphoneos/NormalBias.app
xcrun devicectl device process launch --device "$IPHONE_UDID" \
  --terminate-existing dev.normalbias.client
```

Keep signing accounts, certificates, private keys and provisioning profiles in Xcode/Keychain or ignored generated output. Complete any credential prompts yourself. The tested Personal Team profile expires on 2026-11-27; future profiles may differ. First launch may require manually trusting your developer profile under **Settings → General → VPN & Device Management**. Accept the app's Local Network prompt when it first connects. This trial's origin is build-time configuration: rebuild/reinstall if the Mac address or port changes.

Exit criterion: the native app shows the disposable catalogue and search results. Disconnect/reconnect the development connection to check visible failure and recovery.

## 4. Implement native foreground playback

The foreground adapter and build-time development origin are implemented for this iPhone trial; physical acceptance is still required:

- Add native media selection for the chosen platform, evaluating Codename One's framework media API first. The working desktop simulator adapter is a reference, not evidence of mobile support.
- Keep the existing browser bridge and Java queue policy working. Support open, play/pause, seek, completion, error/retry and release on clear/replacement/destruction.
- Add the native development API-origin setting and debug network configuration from step 3. Keep credentials and signing material outside Git.
- Run the API/CLI regression suite, client build/queue tests and browser smoke suite using the documented commands. Build the selected native target again and reinstall.

Exit criterion: audible playback and the existing controls work on the physical phone, with no regressions in the browser client.

## 5. Run the device acceptance checklist

Use at least two distinct synthetic MP3s so track transitions are observable. Record phone/OS, source revision, tool versions, build command, artifact path and results.

- Launch, load catalogue, search and navigate results.
- Play; hear sound and observe progress. Pause; confirm silence and stopped progress. Resume.
- Seek while playing and while paused; require an immediate position jump. A paused seek must remain paused.
- Add a second track without interrupting the first; use previous/next and verify automatic completion advance.
- Interrupt API access, observe the error, reconnect and retry. Clear the queue and verify playback stops.
- Check compact layout, navigation and long metadata on the actual screen.

Capture a short recording of the final app with actual sound for playback evidence. Update [verification](verification.md) with commands, outcomes and any blockers. Installation alone does not verify playback.

## 6. Decide the following increment from the results

If foreground listening passes, test screen lock/background playback, headset/system commands, metadata and interruption handling as the next milestone. Background playback and embedded artwork were subsequently authorised; persistent playlists, deployment and CarPlay remain separate decisions. If packaging or playback fails, retain the exact failure and make that the next bounded investigation.

## Status of this plan

The original plan was documentation-only. Execution began on 2026-11-20 on an Apple silicon Mac with Java 21.0.9, Maven 3.9.12 and Xcode 27.2. The pinned 7.0.282 `ios-source` route successfully generated `client/common/target/client-common-0.1.0-SNAPSHOT-ios-source/NormalBias.xcodeproj` without an `ios/pom.xml`, using the existing hosted iOS profile. It does not submit a cloud build.

The generated stub probes optional callback interfaces against the application class; a `final NormalBias` makes that generated Java fail compilation with `NormalBias cannot be converted to URLCallback`. Keeping the lifecycle entry class extensible fixes generation without a dependency upgrade.

The first unsigned device compile requires Xcode's separately installed Metal Toolchain. If Xcode reports `cannot execute tool 'metal' due to missing Metal Toolchain`, install it with `xcodebuild -downloadComponent MetalToolchain`, then retry:

```sh
xcodebuild \
  -project client/common/target/client-common-0.1.0-SNAPSHOT-ios-source/NormalBias.xcodeproj \
  -scheme NormalBias -configuration Debug -sdk iphoneos \
  -destination generic/platform=iOS \
  -derivedDataPath client/target/device-trial/DerivedData \
  CODE_SIGNING_ALLOWED=NO build
```

This command passed on 2026-11-20 after installing Metal Toolchain 27B47. Output: `client/target/device-trial/DerivedData/Build/Products/Debug-iphoneos/NormalBias.app` (arm64, unsigned). An unsigned `.app` is build evidence, not an installable signed app or physical playback evidence. For signing, use Xcode's Apple Accounts and the generated project's Signing & Capabilities settings; keep certificates/private keys in Keychain and provisioning outside Git. Use a Personal Team if the actual Xcode result permits it.

To pair with current Xcode, connect/unlock/trust the iPhone and open **Xcode → Open Developer Tool → Device Hub** (or **Manage Devices…** in the run destination menu). On the phone, enable **Settings → Privacy & Security → Developer Mode**, restart, unlock and confirm **Turn On**. The switch may appear only after initiating Xcode pairing. See [Apple's instructions](https://developer.apple.com/documentation/xcode/enabling-developer-mode-on-a-device).

See [verification](verification.md) for actual results and remaining gates, and [native readiness](native-build-readiness.md) for earlier investigation and cloud alternatives. The baseline passed signing, installation and process launch on the selected phone; Robin confirmed the screen renders. The final foreground integration build also passed signing/install/launch, and Robin confirmed both tracks load and audible playback works. Robin also confirmed all playback/queue checks pass and search works. Search submission and Reset now open Library to reveal catalogue results when starting from Queue. Robin confirmed the updated search navigation and network recovery checks pass: Wi-Fi off → Search error → Wi-Fi on → Reset restores the catalogue. Final layout/long-metadata acceptance and the saved device recording remain unconfirmed and are tracked separately in verification.md.


## Retained two-album trial library — 2026-11-20

Robin explicitly authorised the 24 MP3s in `$HOME/Uploads` for a private two-album listening trial. They were uploaded with the Java CLI (12 Oasis, 12 Roxy Music), together with the existing two synthetic clips, into the ignored `data/device-trial-albums/` directory. All 24 source-file SHA-256 hashes match imported catalogue IDs. The source files are unchanged. No real music is committed or used in automated test fixtures/recordings.

The phone's same private endpoint now serves this retained 26-track library. It is **not** the disposable demo helper: stopping the API preserves the catalogue and audio. Start it from the repository root with Java 21, adjusting the private bind if needed (an address change also requires rebuilding the phone's origin). When working in another Git worktree, point `NORMALBIAS_TRIAL_DATA_DIR` at the **existing** retained library rather than creating an empty catalogue in the new checkout. The 2026-11-20 follow-up uses the earlier worktree's library, as recorded in [verification](verification.md). Stop the current service before opening the same directory elsewhere:

```sh
export NORMALBIAS_TRIAL_DATA_DIR="${NORMALBIAS_TRIAL_DATA_DIR:-$PWD/data/device-trial-albums}"
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar \
  --server.address=192.168.1.205 --server.port=18082 \
  --normalbias.data-dir="$NORMALBIAS_TRIAL_DATA_DIR" \
  --spring.web.resources.static-locations="file:$PWD/client/target/web/"
```

To import again or maintain this library with the CLI, stop its private-bound service first and temporarily start the **same data directory** bound to `127.0.0.1:18083`. Then run:

```sh
java -jar cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar "$HOME/Uploads" http://127.0.0.1:18083
```

Exact-file duplicates are reused. Stop that loopback service before restarting the private command above; never run two APIs against the same data directory. Do not run the disposable `demo-device-api.sh` on an occupied port or point its cleanup at this retained library. There is no new public bind, cloud build, deployment or change to normal API defaults.

The UI remains a paginated track catalogue. Search for `Oasis`, `Roxy Music` or an album title to inspect that album; it does not yet group albums. The latest API extracts embedded ID3v2 JPEG/PNG covers on demand, including existing imports. Tracks without usable art retain their title-based generated sleeves. The (…) player action now navigates directly to Queue, with track information on the header info action.

## Background/audio-artwork acceptance — 2026-11-20

The shared Java framework adapter now survives lifecycle `stop()`; `destroy()`, Clear queue and replacement still clean up. `codename1.arg.ios.background_modes=music` generates `UIBackgroundModes` containing `audio`. The pinned iOS port configures `AVAudioSessionCategoryPlayback`. Completion invokes the Java player's poll on the EDT so queue advance does not depend on the visible form's timer. Returning to the app shows the same player/queue. The follow-up adds iOS MediaPlayer lock-screen controls and coherent title/artist/album/artwork through a narrow native interface. Commands return to the same Java player on the EDT. Previous/Next are enabled only within the finite queue, system seek uses milliseconds, paused metadata has rate zero, and clear/queue completion removes system metadata and command targets. Automatic interruption recovery remains separate.

After building/signing/installing with the commands above, test on the physical phone:

1. Play and switch to another app for at least 30 seconds; confirm uninterrupted sound and continued position on return.
2. Play and lock for at least 30 seconds; confirm uninterrupted sound and continued position after unlock.
3. Queue two tracks, seek close to the end of the first, lock, and confirm the second starts while locked. Repeat app switching across a transition; confirm the queue finishes once at its boundary.
4. Pause, switch/lock/return; confirm it stays paused. Recheck foreground seek/next/previous and Clear queue.
5. Check real covers in Library, Now Playing and mini-player. Switch quickly between covered/uncovered tracks; confirm fallback replaces the prior art and late requests cannot overwrite the current selection.
6. Confirm search, pagination, catalogue failure/retry and audio retry still work. Network loss, calls/headphones and system commands require separately recorded results; do not infer them from these checks.

Capture synthetic-only phone evidence with actual sound where supported; do not record personal music. See [verification](verification.md) for the installed build and acceptance results.

The retained-library artwork check distinguishes embedded art from missing tags: all 12 Roxy Music files contain an embedded image; the 12 Oasis MP3s and two synthetic demo clips contain none. Generated sleeves for those 14 files are expected. Search for `Roxy` to check extraction. That embedded-artwork trial had no external cover fallback. The later [album-artwork increment](album-artwork.md) adds explicitly accepted fetched covers for missing images, with no tag writing; new phone verification of that increment is excluded from this session. Native lock-screen metadata is updated on track/state/duration/queue-boundary changes and explicit seeks; iOS advances its own elapsed clock between updates so routine Java polling does not overwrite an in-progress scrub gesture. The selected track's catalogue duration remains available until the native adapter reports a positive duration, including after background queue transitions.

Final physical acceptance on 2026-11-20: Robin confirmed lock-screen seeking works both paused (position jumps and remains paused) and playing after the final build. Background app switching, screen lock, queue advance while locked, paused-state preservation, system play/next/previous and stopping sound on Clear were separately confirmed. The agent directly checked native covered-track rendering and lock-screen metadata/pause through Device Hub. These results apply to the selected iPhone 15 Plus / iOS 27.2.1; they do not establish Android, CarPlay, calls/headphones, long-term background reliability or a saved physical-device recording with sound.

## Fetched-artwork follow-up — physical verification deferred

The album matching/preview/accept flow and revisioned artwork refresh are implemented in shared Java and verified in the browser against a disposable synthetic API. Robin explicitly requested no iPhone connections in this session to avoid conflicting runs. No new native generation, signing, install or phone UI action was performed. The existing private service and retained album library remain untouched. The iOS path requests fresh system artwork after acceptance without reopening media or resetting the system timeline, but lock-screen refresh/background/seek after a fetched cover remains unverified. Do not treat the prior embedded-cover acceptance above as evidence for it. See [verification](verification.md) and the [artwork guide](album-artwork.md).
