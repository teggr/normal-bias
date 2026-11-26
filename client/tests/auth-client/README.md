# Shared-client authentication acceptance

Run from the repository root with **Java 21** on PATH. These tools use an ignored, disposable library and original synthetic music. They must never point at the retained service, personal library or physical phone. The production app has no dependency on these tools.

1. Build with `mvn -B -ntp verify` and `./client/build-web.sh`.
2. Run `python3 client/tests/auth-client/fixture.py` (or `--port 19444` when the default port is occupied). It refuses an existing fixture data directory, listens on loopback HTTPS port 18444 and keeps accounts, certificate/key, protected configuration and synthetic tracks under `client/target/auth-client/`. Stop its owned service when finished. Archive the entire output directory before another fresh run. Never overwrite a running fixture or reuse it for personal data.
3. Run `node client/tests/auth-client/browser.cjs` (optionally `RECORD_DEMO=1`) and `node client/tests/auth-client/resilience.cjs`. `CHROMIUM_PATH` can select Chrome/Chromium; macOS Chrome is the default. The browser pins only the fixture certificate's public key. It does not disable general TLS validation or autoplay policy. The expiry test really waits 121 seconds; it does not modify clocks/server lifetimes. All normal browser interactions are actual canvas pointer/native editor input.
4. For Apple's simulator, run `python3 client/tests/auth-client/prepare-ios.py`, then `NORMALBIAS_API_ORIGIN=https://127.0.0.1:18444 client/target/auth-client/client/build-ios-trial.sh`. Build the generated project for the existing simulator with ad hoc signing (`CODE_SIGNING_ALLOWED=YES CODE_SIGN_IDENTITY=-`), trust the fixture's public certificate **only in that simulator**, and install the app. `ios.py` currently selects the documented iPhone 17 Pro simulator UUID; review it before running on another Mac. Never substitute a physical device. Run `python3 client/tests/auth-client/ios.py`.

The ignored native copy adds `ClientProbe.java` and test-only access to the existing player; production login/networking/media implementations are retained. MCP listens on loopback port 18868. Tests drive the real form's fields/pointer events and inspect actual media state. Native policy/background checks use explicit player controls; the recorded flow uses actual accessibility/pointer input. The probe never returns passwords or session/grant tokens. Terminate the test app to close the socket afterwards; do not distribute this build.

For native recording, compile `client/tests/auth-audio/capture-audio.swift` to the fixture directory and `audio-process.swift` to identify **only** `dev.normalbias.client`'s Core Audio process object (not its OS PID). Start the existing `client/tests/auth-audio/record.py` with fixture/output directories, simulator UUID and that object ID. With the synthetic app paused on Now Playing, run `python3 client/tests/auth-client/record-flow-ios.py OUTPUT_DIRECTORY`. It uses real controls, writes state/time evidence and stops capture even on failure. A slider's accessibility increment did not deliver a media seek in this port, so the recorded seek uses a pointer press/release at its centre. Do not present failed takes.

Mux native screen/process audio using `timing.json`'s measured audio lead; trim both from the same host-clock action start. Preserve paused/ending silence. Use H.264/AAC, yuv420p and fast-start MP4. `verify-video.cjs MOVIE [SAMPLE_TIMES_JSON]` plays the **saved** export, measures real decoded audio and requires audible first/resumed segments, silent pause/logout and a clean ending. Default sample seconds match the short browser demo; pass appropriate `first`, `paused`, `renewed`, `ending` values for another recording. The playback-only artifact verifier permits autoplay, while application acceptance uses normal browser policy.

Record exact results and remaining limitations in `docs/verification.md`. Simulator checks and captures do not establish physical-device, installed-PWA, interruption or uninterrupted locked-output acceptance.

### Account-flow and installed-app checks

After the web build and loopback fixture are ready (Java 21 on PATH):

```sh
node client/tests/auth-client/accounts.cjs
RECORD_DEMO=1 node client/tests/auth-client/accounts.cjs
node client/tests/auth-client/installed.cjs
python3 client/tests/auth-client/ios-accounts.py
```

`accounts.cjs` drives the actual generated forms with pointer/native-editor input, including double submission, wrong current password, revocation, used/revoked links, conflicting usernames and a server-committed response deliberately lost in transit. Forwarding that request uses the fixture CA with normal hostname validation. The native script uses only the simulator probe prepared above; it never targets the physical phone. The probe reads non-secret form messages and excludes field contents.

`installed.cjs` installs a real Chrome PWA in `target/auth-client/installed-profile`, explicitly selects standalone mode, launches real app windows, and checks close/reopen, offline recovery, logout and app-shell cache contents. It removes the installation afterward. It uses the documented [Chrome DevTools PWA domain](https://chromedevtools.github.io/devtools-protocol/tot/PWA/); this is an actual installation, not a display-mode mock. `RESTART_FIXTURE=1` additionally stops **only** the PID whose command matches this fixture configuration, restarts its real API with the same H2 data directory, verifies restoration, then stops the replacement on exit. Run this option **last**: the original fixture supervisor also exits when its API stops. Other tests require a running fixture.

### Separate physical-device fixture

Only for explicitly authorised phone trials:

```sh
python3 client/tests/auth-client/fixture.py --private-host=192.168.1.205
NORMALBIAS_API_ORIGIN=https://192.168.1.205:18445 ./client/build-ios-trial.sh
```

Use `--port=18448 --output-directory=client/target/physical-acceptance` for a fresh parallel fixture when the standard device fixture is retained. An explicit output directory must be fresh/empty and resolve beneath ignored `client/target`; the helper refuses another destination.

Replace the address with the Mac's actual RFC1918 IPv4 address. This deliberately binds a separate required-auth HTTPS service on port 18445, with a new matching certificate, accounts and synthetic tracks under `target/auth-device`. It refuses an existing data directory and never opens the retained service's library. The standard fixture remains loopback-only on 18444. No public bind, TLS-validation bypass, persistent-library migration or production credentials are involved. Trust the public fixture certificate on the phone through its normal settings before login; keep the private key/configuration off the phone. Remove that temporary profile/trust after acceptance. See the device guide for the physical acceptance gate and restoration instructions.

`python3 client/tests/auth-client/ios-network.py` exercises an actual fixture outage/restart plus native operator revocation. It verifies the PID's configuration before stopping it, writes credential-free results, and then keeps its owned replacement API alive for the remaining tests. Stop that replacement (after checking its command) to finish; the script then exits. Run it only when other tests are not using the fixture. `installed.cjs` with `RESTART_FIXTURE=1` can take over that same fixture for the final installed-app restart check and stop it on completion.

Export a native capture with `python3 client/tests/auth-client/export-native.py RECORDING_DIRECTORY`. It retains the actual captured audio, trims the measured start lead and exports CFR H.264/AAC using the shorter input duration. Static lock frames are repeated through that duration; it avoids FFmpeg's short-buffer cutoff on sparse simulator video. Inspect/probe/play back the exported file and measure sound; a successful export alone is not acceptance.

### Compact navigation acceptance

After preparing/installing the current ignored simulator build, run `python3 client/tests/auth-client/compact-ios.py`. It uses only the documented iPhone simulator and synthetic fixture. Checks cover the empty player/Queue, Back, full-screen Account Back from Library/Now Playing, password-form cancellation back to Account, queue rows/current track, continuing playback, paused seek/navigation retention, the dedicated Queue control, inline information/artwork swapping, both Shuffle controls with playing/paused retention, Restart while playing/paused and clearing. It leaves the simulator signed in on Library with an empty queue for manual testing. Results and native screenshots are ignored under `target/auth-client/`.

The test-only probe schedules pointer release asynchronously so UI actions do not hold the tool connection while an event is being dispatched. It still uses actual form pointer input and never returns field values or credentials. Browser helpers open Profile before account actions; no product test interface is added.

### Full-screen Account workflow

`RECORD_DEMO=1 node client/tests/auth-client/account-navigation.cjs` uses the existing disposable HTTPS fixture and real generated client. It verifies the absence of a dialog/Close and the presence of an explicitly labelled, read-only authenticated username; Back to Library/Now Playing, including Queue → Now Playing → Account; retained Library query/search visibility; live/paused media continuity; password-form cancellation returning to Account with its launcher retained across resize; and Sign out releasing sound. Capture starts after synthetic login so no password entry is filmed. Its separate ignored artifact directory is `client/target/recordings/account-view/`, with event/timing evidence, screenshots and result JSON. The fixture remains running; browser demo contexts block service workers. `accounts.cjs` remains the separate full account-lifecycle regression.

### Unified connection/sign-in acceptance

With the freshly built web client and disposable HTTPS fixture, run `RECORD_DEMO=1 node client/tests/auth-client/connection.cjs`. It uses actual canvas pointers/native editors to check unreachable startup, retry without credentials, invalid addresses/passwords, session restoration, unrelated-service responses, compact/short/wide layouts and same-tab endpoint navigation without credentials. It starts a second HTTPS API using the fixture certificate, the next port and a fresh `connection-secondary-*` directory, then stops that owned process. Silent video is appropriate for this connection-only flow; final H.264 MP4, screenshots and results live under ignored `client/target/recordings/service-connection/`. Service workers are blocked for fresh-asset checks.

`python3 client/tests/auth-client/connection-ios.py` is the corresponding first-use/native-selection check. Its docstring describes the isolated `dev.normalbias.connectiontrial` build: no bundled origin/native-trial profile, test-only probe on 18878, and the fixture certificate trusted in the simulator. Prepare/install it using the exact local generation/signing commands in the dated verification entry. It preserves the retained app and stops the isolated app signed out. The check requires a fresh isolated-app installation for its no-saved-address assertions.

The connection recorder uses real rendered screenshots because the standard Chrome screencast omitted the canvas on the verification Mac. It records across navigation, preserves real loading transitions and exports a silent H.264 MP4; verify the saved movie’s decoded frames and ending before handoff.

### Simulator review handoffs

A web build does not replace an installed iOS app. When handing a simulator preview back for manual review, identify the exact app and service, and keep the final reviewed preview visible instead of removing it before the user checks it. The isolated ad hoc build may use the display name **Normal Bias Preview** with bundle **dev.normalbias.connectiontrial**, preserving the retained **Normal Bias** app. Its compiled shared theme can be refreshed and the built artifact re-signed for CSS-only checks; record the matching theme hash and distinguish this from a fresh native-code build. A floating Done while the software keyboard is hidden is the native editing accessory, not an application submit action.

### Endpoint readiness and keyboard traversal

With the fresh web build and isolated HTTPS fixture ready, run `RECORD_DEMO=1 node client/tests/auth-client/endpoint-readiness.cjs`. It verifies failure/checking/ready credential gating, a six-second deadline against a held discovery request, rejection of late success, Retry recovery, real Tab/Shift-Tab between native editors, address-entry invalidation and safe sign-in only after verification. The intentionally silent actual-canvas recording ends before the real fixture password is entered; a masked dummy string is used for field progression. It is saved under ignored `target/recordings/endpoint-readiness`. The shared screenshot recorder is `client/tests/capture-silent.cjs`; the existing `connection.cjs` still covers service selection, offline cookie recovery, short/wide layout, duplicate Retry and cross-service navigation without credentials.


### Isolated repeat on another simulator

Set `NORMALBIAS_AUTH_FIXTURE_DIR` to a fresh fixture beneath ignored `client/target`, `NORMALBIAS_AUTH_SIMULATOR` to an available CoreSimulator UUID, and `NORMALBIAS_AUTH_PROBE_PORT` to a free loopback port before preparing/running the native tools. Defaults preserve the original fixture/17 Pro/18868 setup. `prepare-ios.py` injects that probe port into its ignored production-source copy; `ios.py` verifies that the selected UUID belongs to CoreSimulator. These options do not enable physical-device automation. Use a concrete simulator Xcode destination with `ONLY_ACTIVE_ARCH=YES`; do not combine `-arch` with a destination that selects architecture.

For the 2026-11-26 repeat: fixture `client/target/simulator-acceptance`, HTTPS `https://127.0.0.1:19456`, task-owned Plus UUID `36C75114-1816-443B-8330-A53633B1E78F`, probe18908. Trust its public CA only in that simulator with `xcrun simctl keychain <UUID> add-root-cert <fixture>/tls.crt`. Build its copied client ad hoc for the simulator; production account/media code is preserved, test-only MCP is not a distributable app. Run `ios.py`, then `ios-network.py` (keeps its owned restarted API alive), and use the existing process-isolated capture/export tools for the final native controls demonstration. Match the Core Audio process object's OS PID to the launched test app before capturing; never mix unrelated app audio.


After the native outage/revocation suite has left its replacement fixture running, `python3 client/tests/auth-client/grant-revocation.py` checks the grant-side policy using a separate ephemeral listener session. It follows advertised device-login/songs/playback-grant/audio relations, verifies a 206 Range response, revokes exactly that session through the local admin socket, and requires 401 for a later Range, HEAD and grant renewal. It accepts only loopback synthetic HTTPS fixtures under ignored `client/target`, keeps tokens in memory and writes status-only evidence. This does not promise to retract bytes already buffered by a native media player.
