# Agent guide

## Start here

Read [docs/project-brief.md](docs/project-brief.md) before planning or changing this repository. It is the canonical record of the project's purpose, agreed constraints, current scope and open decisions. The [README](README.md) is the entry point.

## Required design reference

Before planning, changing or verifying application UI, read [DESIGN.md](DESIGN.md), the canonical design reference. Follow its navigation, tokens, component, responsive and accessibility conventions; reuse established components and preserve the existing player. Update DESIGN.md in the same change when approved design decisions change, keeping proposals and implementation status distinct. Review the delivered UI against it before handoff. The project brief and required skills below still apply.

## Required skill for Codename One work

Before planning, editing or verifying the Codename One client, read and apply the official [codename-one authoring skill](.agent-skills/codename-one/SKILL.md), then the references relevant to the task. A discovery link lives at `.agents/skills/codename-one`; the canonical directory preserves upstream tool paths. See [upstream provenance](.agent-skills/codename-one/UPSTREAM.md) for the pinned source and refresh policy.

These Normal Bias rules take precedence over generic upstream examples:

- The app directory is `client/`, a separate Maven reactor; the repository root reactor builds only the Spring Boot API and upload CLI. Keep the existing Spring Boot backend, Java/Maven stack and local-only defaults. Upstream's Codename One Backend and Gradle instructions do not describe this project.
- Use the Java 21 JDK; client common sources target Java 17. Codename One and its plugin remain pinned to **7.0.282**. Check API/goal availability against that version before using examples from the current upstream skill. Do not upgrade dependencies or regenerate the app to install or refresh a skill.
- Build the browser client with `./client/build-web.sh`; use the commands and same-origin static serving in [docs/client-poc.md](docs/client-poc.md). This project has no Maven wrapper or separate simulator module. Use `./client/run-simulator.sh` for `cn1:run`; the pinned plugin lacks `cn1:agent`, but the simulator MCP socket and `Sim.java` were verified with `-Dcn1.mcp.port`. See [docs/simulator-poc.md](docs/simulator-poc.md). Cloud tooling remains unconfigured. Do not add cloud credentials/connections or submit builds as part of skill setup.
- Keep UI, API access and queue policy in Java and preserve the documented browser audio bridge. Browser verification does not establish built-in media-adapter, mobile, background audio or CarPlay support. Read the native-interface reference before platform glue changes and the CSS reference before theme changes.
- Follow the existing browser smoke tests and required UI recording workflow for visible changes. Simulator screenshots alone do not verify browser audio. Scope tests to the change and report actual results and limitations.

## Required skill for cloud task handoffs

For every cloud task, read and apply [ui-demo-recording](.agents/skills/ui-demo-recording/SKILL.md) before finishing. UI features and UI bug fixes require a short, verified recording of the final behaviour; audio-related flows require the client's actual sound. Deliver the video in the handoff and record the checks in `docs/verification.md`. For work without a visible UI effect, a recording blocker or an explicit user exclusion, document the specific reason instead. This is a completion requirement, not a request to deploy the app or add an approval step.

## Working rules

- Follow the latest explicit user instructions. Keep the project brief aligned with approved changes; do not promote suggestions to decisions.
- Treat the server as the normal source of truth for application state, business rules and authorisation decisions. Clients should obtain data and decisions through the API wherever practical, rather than maintain competing authoritative state or duplicate server policy. Keep device media execution, transient presentation state and bounded caches client-side where necessary; document exceptions. This principle does not itself authorise migrating the existing session-local playback queue or adding persistent playlists.
- Use HATEOAS approaches in new or changed APIs: return discoverable links/action affordances for operations currently allowed by both the caller's authorisation scope and the resource's state. Clients should follow advertised relations and offer server-backed actions only when advertised, rather than infer permission from roles, construct action URLs or duplicate workflow rules. Recheck authorisation and state on every server request; a previously advertised link is neither a lasting permission grant nor a replacement for enforcement. Handle stale affordances by refreshing state after appropriate authentication, permission or conflict responses. Introduce this incrementally and document compatibility changes; this principle alone does not authorise a repository-wide API rewrite.
- The agreed stack is Java, Maven, Spring Boot, H2 and deploy4j deployment to a single-instance VPS. Do not substitute a serverless API or another language/build system.
- The API-first milestones are implemented. Robin authorised a Codename One client PoC on 2026-11-19: web/JavaScript first, using the existing API; supporting API changes are in scope. Mobile and native media verification follow later.
- Cloudflare object storage remains an option, not a final decision.
- Robin explicitly authorised the first local PoC on 2026-11-17, superseding the earlier hold. Deployment remains out of scope; the web listening-client PoC was subsequently authorised on 2026-11-19.
- Preserve existing work. Keep changes scoped to the requested task.
- Never commit credentials, personal music files, database files or production secrets.
- When implementation begins, document the actual build/test commands and verify relevant changes. Report checks honestly, including those not run or blocked. Do not invent commands or claim deployment/device support without verification.
- Keep durable requirements and decisions in repository documentation so another agent can continue without access to chat history.

## Before finishing a piece of work

Documentation review is part of completing every task. Before the final handoff, review the changes and update the relevant repository documentation in the same piece of work:

- Update [docs/project-brief.md](docs/project-brief.md) when approved requirements, decisions, scope, current status or open questions change. Keep proposals distinct from agreed decisions.
- Update [README.md](README.md) when the project overview, prerequisites, quick-start commands or documentation entry points change.
- Update [docs/local-poc.md](docs/local-poc.md), or the relevant feature guide, when API contracts, response formats, CLI behaviour, configuration, operating instructions or limitations change. Keep examples consistent with the implementation and call out compatibility changes.
- Update [docs/verification.md](docs/verification.md) when checks are run or verification gaps change. Record actual commands, environment, results and checks not run or blocked; preserve historical results with their dates and do not present them as fresh verification.
- Check the documentation against the delivered behaviour and remove or correct stale claims. Run `git diff --check`; run build/tests when appropriate to the change. Documentation-only edits do not require a full build.
- In the final handoff, briefly identify the documentation updated and the checks performed. If no documentation changes are needed, explicitly state that documentation was reviewed and remains accurate, with a short reason.

Do not leave required documentation updates as follow-up work or rely on chat history to explain the delivered state.

## Current repository state

Server authentication is required by default: `/api` requires direct HTTPS, owner/listener permissions and durable H2-backed sessions; audio requires server-issued track-scoped playback grants. Use [docs/authentication.md](docs/authentication.md) for local administration, token files, TLS and API action links. Local commands connect to the running service through an owner-only Unix socket, never a competing H2 writer. The shared listening client implements login, account-link completion, password change and grant renewal. Legacy unauthenticated demos/tests explicitly use `normalbias.auth.mode=demo`, which must remain literal-loopback-only. Do not weaken that gate to restore a private HTTP phone demo or update/restart the retained service without task authorisation.

Java 21 / Maven reactor with Spring Boot API (`api`) and Java upload CLI (`cli`). Run `mvn clean verify` from the repository root. See [docs/local-poc.md](docs/local-poc.md) for the API contract, local commands and limitations. The optional Codename One web client has its own Maven reactor in `client/`; use `./client/build-web.sh` and see [docs/client-poc.md](docs/client-poc.md). UI/API access/queue policy stay in Java; the browser audio native-interface glue is the documented workaround for a framework media failure. The desktop simulator uses the framework media API with FFmpeg/Java Sound; `./client/demo-simulator.sh` prepares a disposable synthetic demonstration. Do not claim this verifies the built-in media adapter or mobile playback.

See [docs/storage-reliability.md](docs/storage-reliability.md) for Flyway migrations, durable operation recovery and online inspection. Inspections run through the API with paginated findings; do not reintroduce the removed offline doctor command. Add new migrations rather than editing applied scripts. Preserve pending-operation evidence and quarantined files. Backup/restore is deferred to deployment.
