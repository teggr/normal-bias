# Agent guide

## Start here

Read [docs/project-brief.md](docs/project-brief.md) before planning or changing this repository. It is the canonical record of the project's purpose, agreed constraints, current scope and open decisions. The [README](README.md) is the entry point.

## Working rules

- Follow the latest explicit user instructions. Keep the project brief aligned with approved changes; do not promote suggestions to decisions.
- The agreed stack is Java, Maven, Spring Boot, H2 and deploy4j deployment to a single-instance VPS. Do not substitute a serverless API or another language/build system.
- Start API-first. Do not begin with a client implementation simply because earlier discussions mentioned Codename One or CarPlay.
- Cloudflare object storage remains an option, not a final decision.
- **The PoC is on hold while Robin sets constraints. Do not scaffold or implement it until explicitly asked to proceed.**
- Preserve existing work. Keep changes scoped to the requested task.
- Never commit credentials, personal music files, database files or production secrets.
- When implementation begins, document the actual build/test commands and verify relevant changes. Report checks honestly, including those not run or blocked. Do not invent commands or claim deployment/device support without verification.
- Keep durable requirements and decisions in repository documentation so another agent can continue without access to chat history.

## Current repository state

Documentation only. There are no established build or test commands yet.
