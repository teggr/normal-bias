# First native build: readiness review

For the proposed local-computer trial, including Android USB API forwarding and the iPhone/Xcode path, see the [local device trial plan](local-device-trial.md). Native generation and physical-device playback remain unverified; the cloud sequence below is an alternative, not a prerequisite for that local trial.

Reviewed 2026-11-19 against merged web PoC `0b20307` plus the local Codename One skill installation. Robin requested starting to investigate Step 4 (reach a device). Target platform and account/signing setup are not yet confirmed; the sequence below is a proposal, not a claim of implemented mobile support.

## Simulator follow-up

Robin subsequently chose a demoable desktop simulator as the immediate increment. That experience is implemented and documented in the [simulator guide](simulator-poc.md). The framework media adapter is selected only for the simulator; native Android/iOS audio and physical-device API access below remain unresolved. The following gaps describe device readiness, not the now-working desktop simulator.

## Current gaps

- `Player` uses `NativeLookup.create(BrowserAudio.class)` and the only supplied implementation is JavaScript. Unsupported non-simulator platforms show “Audio is available in the web client. Mobile support follows later.” A native build alone does not deliver mobile playback. Prefer evaluating Codename One's native media APIs (including background media where appropriate) before adding custom Android/iOS bridges. The browser adapter failure does not establish a native adapter failure.
- `NormalBias.runApp()` obtains the API address from `browser.window.location.href`, falling back to `http://127.0.0.1:8080/`. A physical phone needs a configurable API address it can reach: its own loopback does not refer to the development computer. Keep the API's existing loopback defaults; choose an explicit private development connection or separately scoped secured hosting. Check the target platform's HTTPS/local-network rules for that connection.
- No Android/iOS implementation sources, signing configuration, generated IDE native-build targets or cloud MCP configuration are present. The client POM contains conditional platform profiles; validate the actual packaging path with pinned plugin 7.0.282 rather than assuming current Initializr commands/features all apply.
- A cloud build account/connection, available quota, signing assets and physical device installation have not been verified. No credentials were inspected and no build was submitted.

## Proposed first increment

1. Choose Android or iOS and confirm access to a physical device. For iOS, establish the Apple signing and device provisioning route using the certificate wizard or supported cloud workflow. For Android, establish the development APK/signing route. Keep keys and passwords outside Git.
2. Prepare the selected native target with the existing Java/Maven project and pinned framework. Add a configurable API endpoint and a deliberate development connection to a disposable synthetic library.
3. Implement and locally verify native media selection without regressing the browser bridge. First device acceptance: launch, browse/search, play/pause, seek, queue navigation/completion, visible failure/retry and resource cleanup.
4. Authenticate to Codename One, inspect current account quota and submit one development build. Download and install the artifact; save build ID, result and any actual failure logs. A successful cloud build proves packaging, not device playback.
5. Verify physical-device behaviour and record actual sound. Evaluate screen locking/background playback, headphone controls, metadata and interruptions as explicit subsequent acceptance checks; do not infer them from foreground playback or browser tests. CarPlay is a separate feasibility milestone.

No production deployment, store release or paid subscription is implied by this review.

## Cloud prerequisites and sources

The [official getting-started guide](https://www.codenameone.com/getting-started/) describes account sign-in and cloud native builds; iOS device builds additionally need Apple signing assets. The [pricing page](https://www.codenameone.com/pricing/) checked on 2026-11-19 lists a free tier with 100 monthly credits, 1 credit for non-iOS builds, 8 per iOS build and an 8.5 MB user-JAR limit. Check the actual signed-in account before submission; account availability and remaining credits are unverified.

Apply the [Codename One skill](../.agent-skills/codename-one/SKILL.md), especially `references/build-and-run.md`, `references/cloud-agent.md` and `references/native-interfaces.md`, with the version/project exceptions in [AGENTS.md](../AGENTS.md). No Codename One/BuildCloud connector is exposed in this chat at review time. Installing a repository skill does not connect an external account.
