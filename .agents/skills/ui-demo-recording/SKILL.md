---
name: ui-demo-recording
description: Required completion workflow for cloud tasks that add or visibly change Normal Bias UI features or fix UI bugs. Record the actual client, include real browser audio for audio-related changes, verify the exported video, and provide it in the handoff. Also use when a user explicitly requests a UI demonstration or recording.
---

# Record a UI demonstration

## Decide whether this task needs a recording

Apply this skill to every cloud task before finishing. A new or changed user-visible interaction, layout, state, error message or UI bug fix requires a short recording of the resulting behaviour. A backend change also requires one if it changes a flow the user experiences through the UI. An explicit request for a recording always applies within the requested scope.

Backend-only, CLI-only, test-only, dependency-only and documentation/skill-only changes without a visible UI effect do not require a video. Record a short reason in `docs/verification.md`; do not generate an unrelated clip to satisfy the rule. Follow an explicit user instruction to skip recording or stop work.

Treat the recording as part of finishing the task, not optional follow-up work. Begin preparing fixtures and the capture method during implementation so a recorder failure is discovered early. If recording is blocked, try a reasonable supported alternative and document the specific blocker, what was verified instead and what remains unverified. Do not claim a screenshot, test log, silent clip or inaccessible cloud browser is an equivalent recording. Do not repeatedly direct the user to preview controls absent from their interface.

## Plan the evidence

1. Read the root `AGENTS.md`, `docs/project-brief.md`, `docs/client-poc.md` and the task's acceptance criteria. Respect the user's chosen platform and current scope.
2. Pick a short path that makes the change easy to assess. Aim for 30–90 seconds, adjusting to the task rather than padding the clip. Show the relevant input, action and visible outcome at readable scale.
3. For a bug fix, reproduce the trigger and demonstrate the corrected result. Include a before clip only if the old behaviour was actually reproduced and recording it is safe. Otherwise describe the original problem separately; never stage a fake failure.
4. Include a relevant empty/error/recovery state when the change concerns those states. For playback changes, demonstrate the affected controls and observable progress; for search, show the query and matching/no-match results. Keep playback running during another interaction when that continuity is part of the feature.
5. Use synthetic fixtures and a disposable catalogue. For music, prefer short original synthetic musical clips over a continuous test tone. Do not use personal music, secrets, private library metadata or authenticated pages in the footage. This task does not authorise deploying the app or making the API public.

## Capture the final implementation

- Build the final sources, start the actual API/client and verify readiness using the project commands. Use the generated Codename One client, not a mock screen or hand-written replacement UI. A fresh browser context avoids stale service-worker assets; disclose if service workers are blocked, since that does not verify installed-PWA behaviour.
- Use an available browser/window recorder or project browser automation. Follow the environment/browser tools' guidance. Check installed capabilities first. Do not assume a managed browser can be surfaced to the user just because you can control it.
- On Codename One's canvas UI, use actual input. Accessibility mirrors may have `pointer-events:none`; real pointer input at their current bounds can be necessary. Wait for native editor focus, tab transitions and observable application state before subsequent actions. Avoid reading a hidden tab's text as evidence of the visible result.
- Assert outcomes rather than waiting long enough for them to become true by coincidence. For example, verify a seek jumps promptly relative to the prior position; normal playback eventually reaching that time proves nothing about seeking.
- Capture application audio whenever playback, pause/resume, seeking, queue transitions, mute/volume or interruption handling is relevant. A visual-only change can be silent; identify it as such in the handoff. Never substitute a soundtrack or narration for the client's actual output.
- For separate screen/audio tracks, use a common clock, preserve initial and paused silence, and check synchronisation after muxing. Read [browser capture notes](references/browser-capture.md) for the method used for the first demo and its pitfalls.
- Optional brief captions may explain the next action. Keep them away from controls and results. Describe only what the recording actually demonstrates. Do not edit out a failure and describe the flow as a single successful run. Re-record after changes that affect the demonstrated behaviour.
- Stop test-owned processes afterwards; leave a demo running only when requested or useful for ongoing work. Never terminate unrelated processes.

## Verify the saved artifact

Before presenting a recording:

1. Export a widely playable format, preferably MP4 with H.264 video, AAC audio when needed, `yuv420p` and fast-start metadata. Keep text readable and the file small enough for the chat's artifact limits.
2. Probe the **final exported file** for video/audio streams, nonzero duration, dimensions and size. An audio stream alone does not prove audible output.
3. Play that saved file back. Inspect the feature's key moments and the ending. For audio demonstrations, verify audible decoded samples during playback and expected silence during pauses; check that the picture and sound agree. If sound cannot be monitored directly, measure decoded audio and state exactly how it was checked.
4. Check that captions, query/results, control states and progress are consistent with the claimed outcome. A recording is supplementary evidence: still run the meaningful tests required by the change. Browser success does not prove physical-device background playback, headset controls, audio focus or native integration.
5. If verification fails, repair the capture/export or re-record. If it remains blocked, report the concrete limitation instead of claiming success.

## Deliver and document

- Store generated media and intermediate files under an ignored path such as `client/target/recordings/<task-slug>/`. Do not commit MP4/WebM/WAV files, generated music, H2 data or tool caches. Commit useful reusable capture code only when it belongs to the task, without credentials or absolute environment-specific paths.
- Present the actual video inline where supported and include a clickable file link when needed. Use a verified absolute path returned by the environment; never invent a public URL or offer cloud `localhost` as a URL on the user's own machine. A promised future capture or a log path is not delivery.
- Update `docs/verification.md` with the date, final revision/source state, flow, fixtures, environment, actual capture/test commands, artifact path, playback/audio checks, results and limits. Distinguish historical checks from this task. Record why a clip is inapplicable or blocked when that is the outcome. Preserve existing records.
- In the final handoff, briefly say what the clip shows, whether it contains sound and any material limitation. Link it directly. Include a recording link in a PR description when a PR is requested and the attachment is accessible to reviewers; otherwise state where the task artifact lives. Follow the user's requested commit/push scope and keep generated recordings out of Git.

## Completion check

Before ending a cloud task, one of these must be documented:

- **Recorded:** the final file is delivered and its relevant visual/audio content was verified.
- **Not applicable:** there is no user-visible UI effect; give the specific reason.
- **Blocked or user-excluded:** identify the blocker or user instruction, the alternative evidence obtained and the verification gap.

Keep this check concise; it is not a new approval step.
