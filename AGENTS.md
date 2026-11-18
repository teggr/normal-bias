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

## Current repository state

Java 21 / Maven reactor with Spring Boot API (`api`) and Java upload CLI (`cli`). Run `mvn clean verify` from the repository root. See [docs/local-poc.md](docs/local-poc.md) for the API contract, local commands and limitations.
