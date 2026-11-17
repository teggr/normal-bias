# Normal Bias: project brief and constraints

Last updated: 2026-11-17.

This document records Robin's requirements and decisions. It is the starting point for people and coding agents working on Normal Bias. Distinguish agreed constraints from proposals; do not silently turn an open question into an architectural decision.

## Purpose and context

Normal Bias is a self-hosted personal music service for an existing MP3 collection of approximately 30 GB, intended as an alternative to iBroadcast. Hosting should be in the cloud rather than on a machine at home.

The longer-term listening targets are mobile, CarPlay and desktop web. Robin has expressed interest in Codename One for the client. Client feasibility and implementation are not the first phase.

## Agreed constraints

- **Language:** 100% Java.
- **Build:** Maven.
- **Backend:** Spring Boot.
- **Deployment:** deploy4j to a VPS.
- **API hosting:** a running service on the VPS, **not a serverless API**.
- **Runtime topology:** a single service instance.
- **Database:** H2 for the single-instance service.
- **Starting point:** API-first, covering the music catalogue/index, music management, storage, uploads and library management.
- **Storage option:** Cloudflare object storage (previously discussed as R2) remains an option for music files. It is not yet a final provider decision.

The API-first direction replaces the earlier suggestion to begin with a client playback/CarPlay spike. The Spring Boot/VPS decision replaces the earlier serverless API proposal.

## Current status and scope

Robin is still setting constraints. **Do not start implementing the PoC until Robin explicitly asks to proceed.** The current work is documentation only.

No application, build, deployment or test setup is established by this document. Do not describe unimplemented features as working.

## Open decisions

Resolve these during planning rather than assuming answers:

- Detailed API contract, catalogue model and management operations.
- Upload flow, metadata extraction, duplicate handling and indexing behaviour.
- Final object-storage provider and access/streaming approach.
- Authentication and access control.
- H2 persistence configuration, schema migrations, backups and recovery.
- Java, Spring Boot and dependency versions.
- VPS provider, sizing and deployment configuration.
- Client architecture and Codename One feasibility, including real-device background audio and CarPlay support.

These are planning topics, not a requirement to implement them all in the first PoC.

## Keeping this document current

Record later user-approved constraints and decisions here. Clearly label proposals and unresolved questions. Preserve the distinction between the long-term product and the currently authorised work.
