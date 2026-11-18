# Verification record

## Online inspection replacement — 2026-11-18

Robin requested removal of the offline doctor command and replacement with online inspection. The command and its quarantine action are removed; the API now runs quick/full background inspections with status/progress and paginated read-only findings. The older doctor records below are historical and do not describe the current interface.

Final command on macOS arm64, Maven 3.9.12 and Temurin Java 21.0.9:

```sh
JAVA_HOME=/Users/robintegg/.sdkman/candidates/java/21.0.9-tem PATH=/Users/robintegg/.sdkman/candidates/java/21.0.9-tem/bin:$PATH mvn -B -ntp clean verify
```

**PASS:** full reactor and both packaged JARs; **36 tests, 0 failures, 0 errors, 0 skipped** (3 CLI, 15 existing HTTP integration, 11 migration/recovery, 4 online inspection HTTP integration and 3 inspection concurrency/command-removal tests). Final output: `/tmp/normal-bias-online-inspection-verify.log`. Tests ran outside the sandbox to permit loopback HTTP. `git diff --check` passed.

New verification covers:

- Live POST/GET inspection lifecycle, quick/full modes, 202 and Location, progress/totals, same-size hash corruption versus quick checks, missing/incorrect-size/symlink/orphan/preserved-file findings and unchanged music/catalogue during inspection.
- Background inspection over 14,000 synthetic catalogue entries while ordinary catalogue requests remain available; accurate totals and bounded/default findings pages, empty pages beyond the end and stable retrieval after completion.
- Explicit FAILED state/error on incomplete traversal, invalid mode/UUID/pagination errors, unknown run IDs and eviction after five retained runs.
- Deterministically paused hash reads: another thread can list, delete/re-upload or repair the track before hashing resumes. The run records a changed/skipped track without a stale hash-mismatch finding. Existing quarantined evidence from a repair remains a valid finding.
- An actual in-flight upload body with active staging is excluded from abandoned-file findings. Active tracking clears when the upload completes. A second inspection start is rejected with 409 while the first hash read is paused. The former offline subcommand is rejected before application startup.

**Packaged-process smoke check passed:** `python3 /tmp/normal-bias-online-inspection-smoke.py`. The external temporary harness launched the actual Java 21 API JAR on a random loopback port with synthetic audio and a temporary catalogue. It verified online quick/full jobs, status and stable paginated findings, catalogue/audio availability, report-only hash/orphan detection, pagination validation and rejection of the removed subcommand as a separate process. It stopped the API and removed temporary data afterward. The harness is orchestration, not application/runtime code.

Not verified: full 30 GB hash throughput, long-running concurrent load, physical power loss or operating systems other than macOS for these new checks. Inspection results/history are in memory, retain only the latest five runs and clear on restart. A completed scan is a sequence of revalidated observations, not a frozen library snapshot; changed/skipped tracks and later additions need another run. No online repair/quarantine action or backup/restore was added. See [storage reliability](storage-reliability.md#online-inspection) for the current contract and concurrency semantics.

## Storage reliability milestone — 2026-11-18

Scope: migrations, operation recovery and offline doctor. Backup/restore was explicitly deferred to deployment. All checks used synthetic audio and temporary catalogues; Robin's existing data was not opened or changed.

Final build/test command on macOS arm64, Maven 3.9.12, Temurin Java 21.0.9:

```sh
JAVA_HOME=/Users/robintegg/.sdkman/candidates/java/21.0.9-tem PATH=/Users/robintegg/.sdkman/candidates/java/21.0.9-tem/bin:$PATH mvn -B -ntp clean verify
```

**PASS:** both packaged JARs and the full reactor; **32 tests, 0 failures, 0 errors, 0 skipped** (3 CLI, 15 HTTP integration, 14 storage reliability tests). Tests ran outside the filesystem/network sandbox to allow local loopback sockets. Final Maven output was captured in `/tmp/normal-bias-reliability-verify.log`. `git diff --check` passed.

New storage verification covers:

- Fresh V1/V2 migrations and repeat startup; checked adoption of the original schema preserving edited metadata and identical audio; unexpected legacy schema rejected without baselining; changed migration checksum rejected.
- Prepared-upload replay from staging and replay after its move, using closed/reopened file-backed H2. Repeat replay leaves one track and no pending upload. Corrupt pending bytes stop recovery and remain on disk; a conflicting destination is quarantined while valid staged audio is installed.
- Committed deletion replay before and after audio removal; pending intent retained after filesystem failure; conflicting intent/catalogue state stops recovery without discarding audio; ordinary database deletion failure rolls back its intent and preserves audio.
- Legacy deletion-stage restoration, ambiguous-file preservation and abandoned request-fragment cleanup. Duplicate re-upload repairs missing and same-size corrupt audio without replacing catalogue edits.
- Doctor quick/full modes distinguish missing, incorrect-size and same-size hash failures; symlinks are not followed; orphan reporting leaves bytes untouched unless quarantine is explicitly requested. Process ownership, argument validation and refusal to create a missing catalogue are checked.
- A 14,000-row synthetic doctor catalogue produces accurate totals/counts with a bounded findings page, including pending operations and pages beyond the end. The existing 14,000-track search test still passes: final local HTTP measurements were mean 12.8 ms, p95 26.9 ms, maximum 34.5 ms across 50 sequential requests. These are development measurements, not capacity guarantees.

**Packaged-process smoke check also passed.** Command: `python3 /tmp/normal-bias-reliability-smoke.py`. The temporary external harness launched the actual Java 21 API JAR on random loopback ports and invoked its doctor subcommand as separate processes. It verified fresh startup/migrations; upload/edit/audio persistence across restart; doctor refusing an active API directory; full inspection; pending-deletion reporting and completion after startup; pending-upload reporting and completion after startup; and report-only orphan detection followed by explicit quarantine preserving bytes. Recovery states were seeded in an offline temporary H2 database with the matching H2 2.3.232 shell. All API child processes were stopped and the temporary data directory removed by the harness. The Python harness is task orchestration, not application code or a runtime dependency.

Not verified: physical power loss, forced process termination at every instruction boundary, disk-full/fsync failure, damaged H2 database recovery, real 30 GB/14,000-song full-hash throughput, Windows execution or deployment. Deterministic persisted interruption states and real process restarts were tested. Backup/restore, unattended scheduling and general repair/import of ambiguous evidence are not delivered. See [storage reliability](storage-reliability.md) for supported recovery states and remaining durability limits. Earlier sections below describe their dated implementation state.

## Library management milestone — 2026-11-18

Robin reported completing the initial Mac smoke test before authorising this milestone. The checks below were then run by the agent on macOS arm64 with Maven 3.9.12 and Temurin Java 21.0.9. No personal music or existing catalogue was used.

Final build/test command from the repository root:

```sh
JAVA_HOME=/Users/robintegg/.sdkman/candidates/java/21.0.9-tem PATH=/Users/robintegg/.sdkman/candidates/java/21.0.9-tem/bin:$PATH mvn -B -ntp clean verify
```

**PASS:** all reactor modules and both packaged JARs; **17 tests, 0 failures, 0 errors, 0 skipped** (3 CLI and 14 live HTTP API tests). `git diff --check` also passed. The initial sandboxed build used the shell's default Java 25 and stopped at the existing CLI socket test because binding was prohibited; it did not reach the API tests. Verification was rerun outside the sandbox with Java 21 to permit loopback sockets.

New coverage:

- Case-insensitive partial search over title, artist, album and filename; literal wildcard/SQL-like inputs; blank search, excessive query length and search pagination.
- Common pagination envelope for unfiltered listing and search; matching totals, default/maximum page sizes, stable ordering, offset navigation and empty pages beyond the end. Invalid bounds return 400.
- Partial catalogue metadata editing, whitespace trimming, clearing nullable fields, unchanged fields, database persistence, unchanged audio/identity, duplicate uploads preserving edits and search reflecting edits.
- Invalid JSON, null/empty/non-object payloads, blank title, non-string values, unknown/read-only fields, control characters and excessive text length rejected without partial updates; missing track returns 404.
- Deletion removes row and audio; get/stream/repeat-delete return 404; identical content can be re-uploaded; missing audio still permits stale-row removal. An intentionally added foreign-key guard forces database deletion failure and verifies that staged audio is restored and remains streamable.

The scale test inserts **14,000 synthetic catalogue rows** into temporary file-backed H2 and makes **50 sequential local HTTP search requests**, each including the exact matching total and at most 200 items. It covers common, rare and absent matches, followed by assertions on list/search pagination. Final observed request latency: **mean 13.6 ms, p95 25.3 ms, maximum 32.8 ms**. This is a local development measurement with short synthetic metadata and a warm database, not a VPS capacity guarantee, concurrent-user benchmark or real-collection test. No hardware-dependent timing threshold is asserted.

Not run for this milestone: a new packaged-process/restart smoke test, 30 GB upload, personal collection validation, cold-cache or concurrent-load benchmark, forced crash/power-loss recovery, deployment or device playback. Audio and H2 changes still do not form one atomic cross-resource transaction; deletion crash windows are documented in the local guide. No schema changes were required.

## Initial PoC — 2026-11-17

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
