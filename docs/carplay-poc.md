# CarPlay feasibility PoC

Authorised 2026-11-20: prove Codename One's Java car API in its desktop head unit, generate/compile a local iOS project and establish the signing barrier before buying Apple membership. Robin has a real CarPlay unit and currently uses unpaid developer testing. Real-unit acceptance follows later. Keep this work separate from album matching/artwork.

## Reuse and scope

`CarListening` projects the existing `LibraryApi`, `Track`, `Player` and finite session queue. Phone and car share one player instance; selection, pause, retry, navigation and Clear use the same Java methods. Disconnect preserves playback and the queue. No second audio engine, catalogue, album model, theme or artwork pipeline was added.

The mobile Graphite components remain in the phone app. CarPlay renders system templates rather than Codename One forms or mobile CSS. The PoC uses framework list templates for Library, Queue and playback actions, plus `CarNowPlayingTemplate`. Native Now Playing relies on the existing iOS `SystemAudio` metadata/artwork/remote commands. Desktop Now Playing is a framework placeholder; the playback-action list makes the Java flow testable there. This navigation is a feasibility choice, not an approved final car design. Browse artwork and album grouping are outside this increment.

Library/Queue pagination reserves rows for navigation and respects the connected head unit's row limit. Loading, empty and failure states are explicit. A 16-second Java deadline makes Library retryable if the platform does not deliver its configured network timeout; late replies and replies to destroyed screens are ignored. Retry does not imply cancellation or recovery of every underlying platform network operation.

## Desktop test

Select Java 21, Maven and FFmpeg as in the [simulator guide](simulator-poc.md). First terminal:

```sh
./client/demo-simulator.sh
```

This starts the disposable, synthetic two-track API and normal phone simulator. Keep it running. Its **Car → Connect CarPlay** menu opens the framework head-unit window for manual use, sharing the phone's player/queue.

For repeatable callback/recording checks, run in a second terminal:

```sh
./client/tests/run-carplay-probe.sh
```

This opens a separate probe simulator using the same disposable API. It compiles `client/tests/CarPlayProbe.java` into ignored output, outside application sources, and adds a loopback-only development MCP tool on port 18866. Third terminal:

```sh
python3 client/tests/carplay-smoke.py
```

The script connects the actual framework head-unit renderer and invokes the template callbacks that selections dispatch. It asserts shared player/queue state and records empty Queue, library selection, play/pause, previous/next, the Now Playing placeholder and Clear, then verifies a fresh connection. It does not synthesize OS mouse input or establish native touch/rotary behaviour. The framework recorder captures real Swing head-unit frames and decoded application PCM on its own clock, under ignored `client/target/recordings/carplay/`.

For a different disposable port, pass the same loopback `NORMALBIAS_API_ORIGIN` to both simulator launchers; `NORMALBIAS_DEMO_PORT` configures the demo API. `NORMALBIAS_CARPLAY_MCP_PORT` overrides the probe/test port. Do not run smoke checks against a personal library. Close both task-owned simulators afterward; the demo helper stops its API and deletes its disposable catalogue.

## Local iOS preparation

No dependency upgrade, cloud build, Apple account change or deployment is involved. Select Java 21 and a deliberate private development origin using existing trial validation:

```sh
NORMALBIAS_API_ORIGIN=http://192.168.1.205:18180 ./client/build-carplay-poc.sh
xcodebuild \
  -project client/common/target/client-common-0.1.0-SNAPSHOT-ios-source/NormalBias.xcodeproj \
  -scheme NormalBias -configuration Debug -sdk iphoneos \
  -derivedDataPath client/target/carplay-xcode CODE_SIGNING_ALLOWED=NO -jobs 4 build
```

Replace the private address/port with the intended development API. Unsigned compilation is not installation or native CarPlay verification.

Pinned 7.0.282 contains the Java API, desktop renderer and native bridge. Its scanner emits `CarPlay.framework`, native callbacks, a CarPlay scene entry and `CN1_USE_CARPLAY`. The local `ios-source` configuration is incomplete. The CarPlay helper runs the ordinary generator, then applies bounded adjustments **only to generated output**:

- Declare multiple scenes and `CPTemplateApplicationScene`, preserving the phone scene.
- Route CarPlay's scene role to the framework car delegate; the generated app delegate otherwise always chooses the phone delegate.
- Create `com.apple.developer.carplay-audio` entitlements and connect Debug/Release app signing to the file. Declaring this requirement does not grant it.
- Retain the native bridge singleton's image table under the non-ARC memory model, avoiding an expired autoreleased dictionary on disconnect.

`prepare-carplay-project.py` checks expected source shapes and refuses unknown signing configuration. It is idempotent. Use `build-carplay-poc.sh` after regeneration; raw `build-ios-trial.sh` does not apply these CarPlay preparation fixes. No vendored skill, dependency cache, phone CSS or album-art source changes. Review the workarounds when the pinned framework changes; they compile but remain unverified on an entitled device.

## Signing result and next gate

The prepared arm64 project builds unsigned. The existing development team's automatic-signing check failed with:

```text
Entitlement com.apple.developer.carplay-audio not found and could not be included in profile.
```

No provisioning-update/device-registration flags were used. No account capability, membership, profile registration or device installation changed. The current signing setup cannot produce the CarPlay-enabled app; purchasing membership alone is not evidence of CarPlay approval. Developer Mode does not replace an authorised provisioning profile.

Before real-unit testing, establish Apple's audio entitlement/account eligibility and obtain the matching App ID/profile. Then sign the prepared project, validate native scene launch and system metadata/commands in Apple's CarPlay Simulator, and test the parked real unit. Include cold launch, simultaneous phone/car UI, locked playback, queue completion, interruptions and reconnect. The private API must remain reachable from the phone; wireless CarPlay networking is a separate check. No public API exposure or membership purchase is authorised by this PoC.

Account-specific request check on 2026-11-20: opened Apple's CarPlay entitlement request in Chrome. Robin signed in and personally accepted the Apple Developer Agreement. Apple then redirected to `/unauthorized/`, displaying “Your account can’t access this page.” The current free account therefore cannot access the request form. The page's generic notice also mentions program members whose Account Holder has not accepted the latest licence; it does not explicitly diagnose this account's missing membership. Apple's public guidance separately requires Developer Program membership and a category entitlement for CarPlay apps. Paid enrollment is the expected next route, not a guarantee of entitlement approval; Apple Support can confirm eligibility before a purchase. No CarPlay request was submitted and no membership was bought.

Sources checked 2026-11-20: [Codename One car API](https://www.codenameone.com/blog/carplay-android-auto-codename-one/), [Apple scene configuration](https://developer.apple.com/documentation/carplay/building-your-apps-initial-carplay-interface), [Apple provisioning](https://developer.apple.com/documentation/carplay/preparing-your-ios-app-for-carplay-development) and [Apple simulator requirements](https://developer.apple.com/documentation/carplay/testing-your-app-in-carplay-simulator). Actual dated evidence is in [verification](verification.md).

## Resume checkpoint — 2026-11-20

Robin requested saving this work in a separate PR for later continuation. A different Apple account is an option to investigate, not a selected account or authorisation to switch now. No further account changes, purchase or entitlement submission are part of this checkpoint.

Saved in [draft PR #10](https://github.com/teggr/normal-bias/pull/10), branch `codex/carplay-poc`, based on `main` after merged album-art PR #9. The PR remains unmerged; its account/profile and native runtime gates are intentional resume checkpoints.

When resuming:

1. Choose an Apple account/team Robin owns or is authorised to develop under. Check its membership, role, agreements and CarPlay audio capability before assuming it can grant access; a different free account alone is not evidence that the restriction disappears.
2. With the selected account, revisit the entitlement request and establish whether an audio grant/profile is available. Ask Apple Support about development-only eligibility before paying if desired. Do not reuse another account's signing credentials or assume the existing team/profile applies.
3. Regenerate using `build-carplay-poc.sh`, select that team's matching development profile and sign locally. The current unsigned build and request denial are already documented; no reason to repeat the Java prototype merely to change accounts.
4. Verify native scene launch in Apple's CarPlay Simulator, then the parked real head unit with a reachable private API. Treat cold launch, phone/car coexistence, locked queue advance, system controls and reconnect as acceptance gates.

The verified demo remains an ignored local artifact at `client/target/recordings/carplay/carplay-demo.mp4`, delivered in chat. It is not a public GitHub attachment and must not be added to Git with music, signing files or generated native build products.
