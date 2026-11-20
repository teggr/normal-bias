# Local device trial plan

Proposed 2026-11-20 against `d137316`. This is a trial plan, not a verified native-build guide. Android/iOS target selection remains open. Keep Java 21, Maven and Codename One 7.0.282; do not upgrade the framework just to follow newer examples.

Goal: build on your own computer, install on one physical phone, and browse and play a disposable synthetic library without deploying the service. Start with foreground playback; background audio and CarPlay need separate trials.

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

The existing `NORMALBIAS_API_ORIGIN` launcher override is simulator-only; it is not a phone setting. Native API configuration and platform network policy need implementation. A phone's loopback address normally means the phone itself, except when explicit USB forwarding is active.

Exit criterion: the native app shows the disposable catalogue and search results. Disconnect/reconnect the development connection to check visible failure and recovery.

## 4. Implement native foreground playback

This is application work required before a listening trial, not configuration the user can perform on the current app:

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

If foreground listening passes, test screen lock/background playback, headset/system commands, metadata and interruption handling as the next milestone. Real artwork, persistent playlists, deployment and CarPlay remain separate decisions. If packaging or playback fails, retain the exact failure and make that the next bounded investigation.

## Status of this plan

Repository/documentation review only. No Android SDK setup, native generation, signing, device installation or playback was performed while writing it. Local native builds are supported in the upstream guidance, but this project's pinned build route remains to be proven. See [native readiness](native-build-readiness.md) for earlier investigation and cloud-build alternatives.
