# Local device trial plan

## Simulator continuation completed — 2026-11-26

Robin switched acceptance to the simulator. The physical phone has been restored/launched on the prior working HTTP build. The new physical HTTPS18448 fixture and public-certificate18449 server are stopped; phone certificate setup is no longer needed for this run. The isolated Plus/iOS26 simulator passed remembered sign-in, real grant expiry/seek/renewal, service restart/reconnect and native revocation, with separate later Range/HEAD/renewal rejection checks. A verified native recording contains actual simulator app audio. This closes simulator gaps; physical authenticated/audio/interruption acceptance remains open. Retained HTTPS18445/HTTP18447 services are unchanged. See verification for evidence and limits.

## Authenticated physical acceptance in progress — 2026-11-26

Robin requested closing remembered sign-in, seek/reconnect, renewal/revocation and actual device-audio gaps. A fresh synthetic-only required-auth service is running at `https://192.168.1.38:18448`, isolated under `client/target/physical-acceptance`; both existing services are unchanged. The current signed HTTPS build is installed/launched on the Plus, replacing the HTTP app for this trial. Its fresh certificate is not yet trusted by the phone; the app correctly shows the certificate error and disables credentials. Install/trust only the public “Normal Bias acceptance 26 Nov” profile from `http://192.168.1.38:18449/normal-bias-acceptance.mobileconfig`, then Retry. QuickTime offers phone Screen/Speaker capture and shows a live preview; no actual device-audio capture/acceptance is complete yet. Remove test certificate trust after acceptance. The previous HTTP signed app is retained under `client/target/http-device` for restoration. See verification for commands, process IDs and pending checks.

## Scrolled artwork fix installed — 2026-11-26

Corrected the shared thumbnail viewport clipping so covers remain visible as later rows enter the list. Rebuilt, signed, installed and launched the HTTP trial update on the physical Plus; Device Hub confirmed Library opens with covers. Browser pixel checks passed with 36 synthetic tracks, including later API pages and fixed chrome boundaries; Robin subsequently checked the physical phone and confirmed the artwork now looks correct. Signing expiry and both retained services are unchanged. See verification for commands and the silent demo.

## Phone layout update — 2026-11-26

The full-size Now Playing face now uses the taller Plus viewport with balanced vertical spacing. Native iPhones remain portrait when rotated; tablets/browser layouts retain adaptation. Final signed HTTP app installed successfully on the physical Plus; initial launch was blocked because the phone was locked, then succeeded after Robin unlocked it. Device Hub confirmed the populated Library. Open Now Playing on the phone to inspect the physical spacing/rotation result. The isolated iPhone 15 Plus/iOS 26 simulator verified the full player and portrait lock. Profile expiry remains 2026-11-27 10:13:37 UTC; this layout work does not complete background/interruption acceptance. See the latest verification record for builds, recording and limits.

## Authorised HTTP phone demo — 2026-11-26

Robin requested enabling HTTP for the iPhone demo after the physical HTTPS connection failure. A separate read-only `private-demo` server mode and updated temporary-library helper are prepared; see the authentication guide. The running demo is `http://192.168.1.38:18447`, with the same 24 Uploads tracks plus two synthetic clips. The required-auth HTTPS service on 18445 remains unchanged.

Launch uses Java 21 with `NORMALBIAS_API_ORIGIN=http://192.168.1.38:18447 NORMALBIAS_DEMO_MUSIC_DIR=/Users/robintegg/Uploads ./client/demo-device-api.sh`. It seeds via loopback, then switches the isolated temporary library to read-only LAN serving. A matching local iPhone trial build would use only the selected host's existing development HTTP exception; no credentials are sent in demo mode. A remembered service selection may still need changing to the HTTP address.

Robin subsequently explicitly approved unencrypted, unauthenticated read-only LAN listening, resolving the initial automatic-approval block. The HTTP app was locally rebuilt, signed, installed and launched on the existing physical iPhone 15 Plus. Signed app: `client/target/http-device/DerivedData/Build/Products/Debug-iphoneos/NormalBias.app`, normal bundle `dev.normalbias.client`, origin `http://192.168.1.38:18447`, background audio enabled, only this host's insecure-HTTP ATS exception, no global arbitrary-load bypass or CarPlay entitlement. Strict signature/profile checks pass; profile still expires **2026-11-27 10:13:37 UTC**. Original signed HTTPS app and generated project are preserved under `client/target/auth-device`; HTTPS service remains untouched.

Current HTTP supervisor PID 67878, API PID 67949; temporary library/log directory `/var/folders/x7/t4s1vm5x7rbd9c3d48yw21_00000gn/T/normal-bias-device.ZjwKON`. Verify the process command before stopping only this helper; its trap stops the API/removes its temporary library. Source Uploads files remain unchanged. HTTP discovery/count/read-only enforcement/audio Range and the generated browser's synthetic play/pause/resume pass. Native installation/launch succeeded, but Device Hub still showed Connecting during initial inspection: ensure the iPhone shares the Mac's LAN and Normal Bias has Local Network permission. Phone connection/playback is not established by host/browser checks. No password or certificate is needed by this HTTP demo. After the endpoint-feedback UX refresh on 2026-11-26, the new signed app was installed/launched and Device Hub directly confirmed the physical phone now opens the populated Library. This supersedes the initial connection blocker, without diagnosing its cause. Phone sign-in/keyboard completion and acoustic/locked/interruption playback are separate checks; no personal music was played by the agent.

Repeat browser acceptance/recording with `NORMALBIAS_HTTP_DEMO_ORIGIN=http://192.168.1.38:18447 RECORD_DEMO=1 node client/tests/private-http-demo.cjs`. It filters to Morning Circuit before capture and records actual synthetic sound; does not modify the catalogue or use account credentials. Physical/audio/background acceptance remains separate.

## Endpoint feedback UX installed — 2026-11-26

Rebuilt and installed the shared connection/sign-in UX on the physical iPhone using the same HTTP demo origin/bundle. The address now has checking/tick/cross feedback; unchecked/failed discovery disables credentials with muted hints, explains the failure and offers Retry. Address editing cancels previous readiness, discovery has a six-second deadline, and field traversal is explicitly ordered. Demo discovery still opens Library without credentials: Device Hub directly confirmed the phone now shows the populated Library, with playback stopped. The earlier connection-failure cause remains unknown; this is not authentication or physical audio/interruption acceptance. Browser keyboard/endpoint checks pass; native Next/Done credential-entry acceptance remains to be checked against a reachable required-auth endpoint. Existing service processes/libraries are unchanged; web assets now contain the authorised shared UX update. See verification for tests, the silent recording and exact native build/install evidence.

## Fresh authenticated trial preparation — 2026-11-26

The authorised disposable service is running at `https://192.168.1.38:18445`, bound only to that private address from worktree `8f1e/normal-bias`. It hosts freshly built web assets, required authentication and separate owner/listener accounts. At Robin’s subsequent request, all 24 MP3s in Uploads (12 Oasis, 12 Roxy Music) were imported through the authenticated API. The trial now contains 26 tracks across three albums, including the two three-minute synthetic clips Morning Circuit and Evening Signal. Source hashes were verified and originals were unchanged. Use only the synthetic clips for recorded playback evidence. Data/TLS/configuration are under ignored `client/target/auth-device`; listener credentials are in its owner-readable `fixture.json`. Its certificate expires **2026-11-28 13:56:28 UTC**. Phone reachability on the same trusted network remains to be checked.

The prepared account-flow app under worktree `7684/normal-bias/client/target/auth-device/DerivedData/Build/Products/Debug-iphoneos/NormalBias.app` bundles the old `https://192.168.1.205:18445`. Its provisioning profile expires **2026-11-27 10:13:37 UTC**; the embedded developer certificate expires 2027-11-20 10:03:31 UTC. The profile sets the earlier deadline. Before installation, refresh the build with Java 21 and `NORMALBIAS_API_ORIGIN=https://192.168.1.38:18445 ./client/build-ios-trial.sh`, use the documented local Xcode signing commands, and verify the new signed origin/profile expiry. No phone build/install occurred in this preparation. Robin then authorised a rebuild/sign for the new service: the current source is now locally built and signed at `client/target/auth-device/DerivedData/Build/Products/Debug-iphoneos/NormalBias.app`, with an installable development package at `client/target/auth-device/NormalBias-phone-trial.ipa`. Strict signature verification, bundled new origin, audio background mode and registered-phone provisioning pass. Xcode reused the existing profile, so **the rebuilt app still expires 2026-11-27 10:13:37 UTC**. No install/launch was performed. Install with the documented `xcrun devicectl device install app` command pointing to this worktree’s signed app (or use Xcode Devices and Simulators). The phone must separately trust the temporary HTTPS CA. Subsequent authorised install: `xcrun devicectl device install app` and `device process launch --terminate-existing dev.normalbias.client` succeeded on Robin’s connected physical iPhone 15 Plus on 2026-11-26. This replaces the prior installed build; the new bundled default is HTTPS 18445. A remembered service selection may take precedence, so check the sign-in Service address is `https://192.168.1.38:18445`. No phone CA trust/login/audio acceptance was established by installation.

Public certificate-only profile: `/Users/robintegg/Uploads/normal-bias-trial-2026-11-26.mobileconfig`. Transfer to the iPhone, install through its normal Settings profile flow, then enable this temporary root under **Settings → General → About → Certificate Trust Settings**. Keep private keys, keystore and configuration on the Mac. CA SHA-256 fingerprint: `96:A5:B5:D9:39:B8:E9:70:D4:65:F2:A9:52:19:3E:6C:41:E0:98:C7:2F:E4:C4:E1:B7:6F:24:A5:DD:40:CD:EC`. Remove temporary profile/trust after acceptance. No phone or Mac trust was changed by this task.

The supervisor remains running for the requested trial. API PID is recorded in `client/target/auth-device/api.pid` (49320 at preparation). Verify its command references this worktree's `server.properties` before sending SIGTERM; the supervisor then exits. Archive a stopped fixture's output before another run. Retained services/library remain unchanged. Physical sign-in persistence, locked next-track sound, interruption, seeking, renewal, revocation and audio recording remain pending; see verification for actual service checks.

**Compatibility checkpoint, 2026-11-23:** the new server revision requires HTTPS/accounts/scoped playback URLs. The physical-client commands and private HTTP route below describe the earlier installed trial, not a supported way to bypass the new authentication gate. The retained service/phone were left untouched. Client authentication is now integrated and checked separately on synthetic fixtures, but migrating the retained service/phone still requires its own authorised HTTPS/account trial; `demo-device-api.sh` cannot serve its private HTTP route with the new defaults, and demo mode deliberately refuses private-interface binds. Use [server authentication](authentication.md) and the separate [native simulator feasibility guide](authenticated-audio-trial.md) for current work.

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

The build helper fixes the Maven target to **local `ios-source`**, requires Java 21, and rejects public/wildcard hosts, credentials, paths and privileged ports. Private HTTP/HTTPS addresses are accepted; HTTPS loopback is accepted for the simulator route. It uses the opt-in `native-trial` Maven profile to bundle `normalbias-trial-origin.txt`; the template contains no machine address. It removes that resource from shared target/classes after generation so an ordinary subsequent browser/simulator package does not retain the address. Do not invoke the profile directly with unchecked values. The generated project has iOS minimum 17 and a Local Network usage explanation. Historical HTTP builds add a single-IP exception with `NSAllowsArbitraryLoads=false`; HTTPS builds add no insecure ATS exception. No global HTTP/TLS bypass is added. This is a deliberate private development connection, not authenticated remote hosting.

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

The updated client offers paginated **All songs**, **Albums** and **Artists** views. Open an artist to browse its albums, then open an album for songs; album groups use exact catalogue artist + album names. Search is inside Library, beneath the graphite view buttons, with amber text for the selected view. The search well matches the player metadata inset. Queue/Now Playing hide library search. The latest API extracts embedded ID3v2 JPEG/PNG covers on demand, including existing imports. Tracks without usable art retain their title-based generated sleeves. The (…) player action now navigates directly to Queue, with track information on the header info action.

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

The retained-library artwork check distinguishes embedded art from missing tags: all 12 Roxy Music files contain an embedded image; the 12 Oasis MP3s and two synthetic demo clips contain none. Generated sleeves for those 14 files are expected. Search for `Roxy` to check extraction. That embedded-artwork trial had no external cover fallback. The later [artwork increment](album-artwork.md) now fills missing covers automatically in the service, with no tag writing or artwork-management screen. Earlier phone exclusions below describe their historical sessions. Native lock-screen metadata is updated on track/state/duration/queue-boundary changes and explicit seeks; iOS advances its own elapsed clock between updates so routine Java polling does not overwrite an in-progress scrub gesture. The selected track's catalogue duration remains available until the native adapter reports a positive duration, including after background queue transitions.

Final physical acceptance on 2026-11-20: Robin confirmed lock-screen seeking works both paused (position jumps and remains paused) and playing after the final build. Background app switching, screen lock, queue advance while locked, paused-state preservation, system play/next/previous and stopping sound on Clear were separately confirmed. The agent directly checked native covered-track rendering and lock-screen metadata/pause through Device Hub. These results apply to the selected iPhone 15 Plus / iOS 27.2.1; they do not establish Android, CarPlay, calls/headphones, long-term background reliability or a saved physical-device recording with sound.

## Fetched-artwork follow-up — physical verification deferred

The album matching/preview/accept flow and revisioned artwork refresh are implemented in shared Java and verified in the browser against a disposable synthetic API. Robin explicitly requested no iPhone connections in this session to avoid conflicting runs. No new native generation, signing, install or phone UI action was performed. The existing private service and retained album library remain untouched. The iOS path requests fresh system artwork after acceptance without reopening media or resetting the system timeline, but lock-screen refresh/background/seek after a fetched cover remains unverified. Do not treat the prior embedded-cover acceptance above as evidence for it. See [verification](verification.md) and the [artwork guide](album-artwork.md).


## Automatic artwork service and simplified phone app — 2026-11-20

The manual image action and matching form are removed. Missing covers are processed by the API without the phone being open; Now Playing information shows track details. Visible clients poll the persisted artwork revision every ten seconds and refresh after a change without reopening media. A suspended phone picks up the update when its form timer resumes. Start the ordinary retained-library service command above to enable background processing by default; append `--normalbias.artwork.background-enabled=false` for isolated offline/synthetic operation. See the [artwork guide](album-artwork.md) and fresh dated [verification](verification.md); earlier manual-flow and no-install checkpoints are historical.


## Artwork refresh recovery acceptance — 2026-11-20

After installing the corrected client, leave it open while a synthetic service gains a previously missing cover. Require the Library, player and mini-player to replace placeholders without relaunching the app, reopening audio, changing queue contents or resetting a paused/playing timeline. Repeat with revision notifications unavailable: missing-image entries expire after 30 seconds independently. Return from backgrounding and require an immediate revision check and recovery of stale missing images; network requests have a 25-second state watchdog. A paused app must stay paused. Use a disposable mocked-provider library for these checks, not edits to retained personal cover associations. See [verification](verification.md) for actual execution/results; earlier fresh-launch-only evidence does not establish this acceptance.


The pinned local iOS generator can report “Sources have not changed” and reuse an existing Xcode project after changing only the bundled trial origin/build settings. When deliberately changing those settings, remove only the ignored `client/common/target/client-common-0.1.0-SNAPSHOT-ios-source/` generated directory before regenerating, then verify the generated `NormalBias-src/normalbias-trial-origin.txt` and the signed app's `CFBundleIdentifier`/display name before installation. Do not reuse a temporary test bundle identity as the normal app. A second app used during the refresh investigation was removed after Robin noticed it; the delivered app keeps `dev.normalbias.client` / Normal Bias and the retained service origin.

### Library-view update installed — 2026-11-20

The updated All songs/Albums/Artists client, inset search and graphite view buttons were locally signed, installed and launched on the existing iPhone 15 Plus. Use the same private origin, `http://192.168.1.205:18082`. The service now runs the API and web assets from `/Users/robintegg/.codex/worktrees/784f/normal-bias`, while retaining `/Users/robintegg/.codex/worktrees/71a0/normal-bias/data/device-trial-albums`. Do not replace that directory with a new worktree's empty catalogue. Service PID/log and restart arguments are recorded in verification. All 26 tracks/metadata and 24 real covers were retained; no upload was repeated.

For this Personal Team route, the local Maven `ios-source` generation explicitly overrides `-Dcodename1.arg.ios.carplay.audio=false`, as in the earlier successful native invocation in verification. The generic `build-ios-trial.sh` helper does not apply that override to the repository's enabled CarPlay hint, so use the recorded native command for the current signing route. Browser/static output remains the previously verified local web build. Fresh phone visual/playback acceptance remains for the user's trial.

### Fixed native player header — 2026-11-20

The follow-up installed a shared Java layout correction: explicit content safe-area handling and a fixed Back/Now Playing/Information header above the scrolling player body. Device Hub observed both controls restored beneath the iPhone status area; the Library brand is visible again too. Browser regression additionally forces the body to scroll in a short viewport and exercises Information/Back. The existing API/library remains running unchanged. Physical visibility is directly verified, and Robin confirmed both header buttons work; detailed evidence is recorded in verification.

## Account-flow HTTPS trial — 2026-11-23

The shared client includes invitation/reset screens, native pasted same-origin HTTPS links, password change, session restoration and playback renewal. Native account-screen and real-expiry/background regressions pass in Apple's simulator. The production helper now explicitly disables CarPlay for this local trial, retaining the repository's general CarPlay hint and avoiding entitlements outside the existing Personal Team route.

Robin authorised a **separate disposable** physical trial: `python3 client/tests/auth-client/fixture.py --private-host=192.168.1.205` starts required-auth HTTPS on port 18445 with matching temporary TLS material, temporary accounts and two synthetic tracks under ignored `client/target/auth-device`. Build with Java 21 and `NORMALBIAS_API_ORIGIN=https://192.168.1.205:18445 ./client/build-ios-trial.sh`. Trust only the public temporary CA on the phone through its normal profile/certificate settings; no certificate-validation bypass is bundled. Keep the private key/configuration on the Mac. Remove the test certificate/profile after acceptance.

The generic iOS production build is signed and verified under the existing Personal Team, but the selected iPhone became unavailable and Xcode's concrete destination timed out before installation. Thus the retained phone app and private service/library remain unchanged. Robin subsequently selected simulator verification instead of the physical phone. No physical authentication, locked-audio, network-interruption, renewal, revocation or recording result is claimed. The earlier lock-recording silence remains unresolved.

To resume: connect/unlock the existing iPhone, prepare a fresh disposable fixture (archive a stopped previous output first), trust its newly generated CA, then build/install the normal production app using the established signing commands and the new HTTPS origin. Verify relaunch restoration, artwork, playing/paused seek and renewal after a real two-minute pause, locked next-track advance, network recovery and operator revocation on subsequent requests; record actual sound. Installation changes the phone app's build-time origin. After the trial, remove temporary certificate trust and reinstall the previously retained build if it is still valid, or rebuild the prior retained source/origin using its documented configuration. Do not point the authenticated build at the historical private HTTP endpoint or restart/migrate the retained service without separate authorisation. No restoration is needed for this task because the physical app was never replaced; after the initial connection blocker Robin selected simulator-only acceptance.

The subsequent simulator-only continuation passed actual API outage/restart/revocation checks. A 93.97-second final recording verifies simulator lock before queue completion and continued next-track sound, with only a 0.70-second transition pause. The old 13-second silence did not recur; its original cause is not established. Simulator evidence does not replace future physical/call/headphone acceptance. The temporary private HTTPS/certificate-only servers were stopped after Robin selected the simulator; no phone certificate was installed by the agent.
