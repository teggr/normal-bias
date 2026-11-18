# Verification record — 2026-11-17

## Environment and build

Executed in the Dot cloud Linux workspace, not Robin's Mac. No personal music was used. The initial environment had a Java runtime but no usable JDK compiler or Maven. Task-local Maven 3.9.11 and Eclipse Temurin Java 21.0.13 were downloaded from their official distribution endpoints. Maven Central dependencies were obtained through the workspace's existing proxy and Java CA trust store; TLS validation remained enabled. No unresolved build/network blocker remained.

Final command (after dependencies were cached):

```sh
JAVA_HOME=/tmp/normal-bias-jdk /tmp/apache-maven-3.9.11/bin/mvn -o -B -ntp clean verify
```

**PASS:** reactor build, executable API JAR and executable CLI JAR. **11 tests, 0 failures, 0 errors, 0 skipped** (3 CLI tests and 8 live HTTP API tests). Normal developer command: `mvn clean verify` with Java 21 JDK selected.

Earlier attempts exposed environment setup issues (Maven proxy configuration, missing JDK and unused Mockito agent attachment). The final build resolves them; Mockito is excluded because these tests exercise real HTTP and do not use mocks.

## Automated coverage

- Upload returns 201; metadata title/artist/album, duration and size are extracted from the synthetic MP3.
- Untagged MP3 falls back to filename. Changing tag bytes creates a distinct exact-file identity.
- List/get, pagination, duplicate 200 response and concurrent duplicate requests resulting in one row.
- Full stream exact byte equality and HEAD length/body behaviour.
- Closed, open-ended and suffix ranges, end clamping, oversized suffix, invalid/unsatisfiable/multiple ranges and unmatched If-Range fallback.
- Empty/invalid/unsupported-type/oversized uploads; oversized chunked upload; missing tracks; invalid pagination; filename traversal cannot choose storage paths.
- CLI recursive traversal, uppercase extension, symlink/file-loop skipping, ignored non-MP3 files, progress output, duplicate retry and failure exit status.
- CLI loopback-only URL validation, rejected symlink root, pre-cancelled traversal and cancellation against a deliberately slow local HTTP receiver.
- Actual API partial-body socket disconnect leaves no row, removes staging data and permits a subsequent valid upload.

## Packaged-process smoke checks

A temporary external test harness launched the actual packaged API on a random loopback port with a new temporary data directory, and ran the actual CLI JAR as a child process. It inspected the listening socket, made HTTP requests, stopped/restarted the API, and sent SIGINT to the CLI. All service processes were stopped afterward. The harness was task orchestration, not application code or a runtime dependency.

**PASS:**

1. Default binding is loopback (Java's IPv4-mapped loopback socket), not a wildcard listener.
2. CLI uploaded the 8,686-byte bundled synthetic tone: one new track.
3. Full audio matched the fixture byte-for-byte; `bytes=10-99` returned 206 and exactly the selected bytes; an out-of-bounds range returned 416. Invalid MP3 returned 422 and missing track 404.
4. After a clean stop/restart against the same data directory, the complete track JSON and stored audio remained identical. A synthetic stale `.part` file was removed at startup.
5. Running the CLI again reported one duplicate and no new row.
6. A second CLI process began uploading a 192 MiB synthetic sparse file. Once 16,384 bytes were observed in server staging, SIGINT was sent. The CLI exited **130**, staging became empty, and the catalogue remained unchanged. This tested real CLI-to-API in-flight cancellation, not just a cancellation flag before work began.

The bundled tone is the only committed binary fixture. Large synthetic cancellation files and H2 databases remained outside the repository.

## Not tested / not delivered

No 30 GB load test, Robin's real collection, Windows/macOS execution, real listening clients/device playback, full malicious-MP3 corpus, forced power-loss/crash recovery, concurrent service instances, public/remote access, authentication, production dependency security audit, deployment, VPS/deploy4j or R2. No byte-level resumable upload protocol. Cross-resource crash atomicity and automatic orphan reconciliation remain limitations documented in [local-poc.md](local-poc.md).
