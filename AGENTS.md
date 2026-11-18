# Agent guide

## Start here

Read [docs/project-brief.md](docs/project-brief.md) before planning or changing this repository. It is the canonical record of the project's purpose, agreed constraints, current scope and open decisions. The [README](README.md) is the entry point.

## Working rules

- Follow the latest explicit user instructions. Keep the project brief aligned with approved changes; do not promote suggestions to decisions.
- The agreed stack is Java, Maven, Spring Boot, H2 and deploy4j deployment to a single-instance VPS. Do not substitute a serverless API or another language/build system.
- Start API-first. Do not begin with a client implementation simply because earlier discussions mentioned Codename One or CarPlay.
- Cloudflare object storage remains an option, not a final decision.
- Robin explicitly authorised the first local PoC on 2026-11-17, superseding the earlier hold. Keep deployment and listening clients out of this implementation.
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

Java 21 / Maven reactor with Spring Boot API (`api`) and Java upload CLI (`cli`). Run `mvn clean verify` from the repository root. See [docs/local-poc.md](docs/local-poc.md) for the API contract, local commands and limitations.

See [docs/storage-reliability.md](docs/storage-reliability.md) for Flyway migrations, durable operation recovery and online inspection. Inspections run through the API with paginated findings; do not reintroduce the removed offline doctor command. Add new migrations rather than editing applied scripts. Preserve pending-operation evidence and quarantined files. Backup/restore is deferred to deployment.
