# Server accounts, sessions and playback URLs

## Opt-in private HTTP listening demo — authorised 2026-11-26

Robin requested an HTTP iPhone demo. A separate `normalbias.auth.mode=private-demo` mode implements this purpose: it requires an explicit literal RFC1918 IPv4 `server.address`, advertises listening discovery with `authRequired=false`, and permits catalogue/group/index/artwork/audio GET plus audio/artwork HEAD. Audio uses the existing direct demo stream with Range support. Login/account/grant endpoints, uploads, mutations, inspection and artwork-management requests are denied; no password/session flow is supported over HTTP. Anyone able to reach this private endpoint can read its demo catalogue/audio. Required authentication still requires HTTPS; existing `demo` mode still refuses non-loopback binds. Public/wildcard/DNS/link-local/ambiguous bind addresses are rejected for `private-demo`.

The updated `client/demo-device-api.sh` seeds an isolated temporary library through loopback `demo`, stops that single writer, then serves it on the selected private address using read-only `private-demo`. Set an explicit HTTP `NORMALBIAS_API_ORIGIN`; optional `NORMALBIAS_DEMO_MUSIC_DIR` adds MP3s through the existing loopback uploader before switching modes. No personal library/database is reused. The supervisor removes this temporary library on exit, leaving source files unchanged. Do not use this route to verify authentication, private credential transport or deployment security.

**Operational status:** Robin explicitly confirmed unencrypted, unauthenticated read-only LAN access on 2026-11-26, resolving the initial automatic-approval block. The separate demo is running at `http://192.168.1.38:18447` with 26 tracks (24 Uploads tracks plus two synthetic clips); the matching signed iPhone app is installed/launched. HTTPS 18445 remains on the original process, configuration, library and credentials. Host transport/browser playback checks pass, and Device Hub confirms the phone opens the populated HTTP Library. Robin confirmed the later-row artwork fix; authenticated physical playback/interruption and device-audio recording remain separate. The subsequent signed-in simulator acceptance passed remembered sign-in, real grant renewal, reconnect and revocation with verified simulator audio. The helper uses a temporary library removed when its supervisor stops, so keep it running for the trial.

Implemented server milestone, 2026-11-23. The API now uses Spring Security 6.5.11, managed by existing Spring Boot 3.5.16, with H2 as the source of truth for accounts, credential validity and allowed actions. The Java/Maven stack, loopback default binding and single data-directory owner are unchanged.

The shared web/iPhone client now integrates login, remembered sessions, logout and scoped playback renewal. The private HTTPS trial uses synthetic accounts/music; no physical phone install or retained-library migration is included.

## Defaults and compatibility

- `normalbias.auth.mode=required` is the default. Every `/api` request requires real HTTPS; plain HTTP returns **426**, and forwarded TLS headers are ignored by default. The public app shell contains no library data. Anonymous access is limited to authentication discovery and credential/one-time-token flows.
- Owners manage music, storage and artwork operations. Invited listeners browse/search/read artwork and request playback URLs. Upload credentials can discover their own upload action and upload; they cannot browse, play, edit, delete, change passwords or inspect storage.
- `normalbias.auth.mode=demo` explicitly restores the earlier unauthenticated API for local demos/tests. Startup rejects this mode unless `server.address` is the literal loopback `127.0.0.1`, `::1` or `[::1]`. Account/session/grant endpoints and the admin socket are disabled in this mode. Never use it for a private-interface or public bind.
- In required mode track/song/album/artist responses add `_links` without removing existing data/pagination fields. Audio **always requires a playback grant**, even if a browser cookie or bearer session is present. Current clients discover playback grants; older direct-URL clients must be upgraded. Demo responses retain direct audio behaviour and include additive navigation links where documented.
- Account administration currently requires a Unix-like host (tested macOS). No network administration endpoint, public registration, email delivery or external identity provider is added. Use root-mounted `/api` links and direct TLS for this local milestone; reverse-proxy deployment/prefix configuration is not delivered.

## Start a private HTTPS service

Build with Java 21 using `mvn clean verify`. Configure Spring Boot's embedded TLS through a **local, ignored, owner-readable configuration file**. Example content (replace placeholders locally; never commit passwords, private keys or the file):

```properties
server.address=127.0.0.1
server.port=8443
server.ssl.enabled=true
server.ssl.key-store=file:/ABSOLUTE/PRIVATE/PATH/development.p12
server.ssl.key-store-password=YOUR_LOCAL_KEYSTORE_PASSWORD
normalbias.data-dir=/ABSOLUTE/PRIVATE/PATH/library
normalbias.auth.mode=required
```

Use a certificate trusted by the client with a SAN matching the development host/IP. Keep its private key/configuration and data directory owner-only. For a local synthetic trial, Java's keytool can create a short-lived development certificate and export its public certificate; leave the store-password argument off so keytool prompts securely:

```sh
keytool -genkeypair -alias normalbias -keyalg RSA -keysize 2048 \
  -storetype PKCS12 -keystore /PRIVATE/PATH/development.p12 \
  -dname CN=localhost -ext SAN=dns:localhost,ip:127.0.0.1 -validity 2
keytool -exportcert -rfc -alias normalbias \
  -keystore /PRIVATE/PATH/development.p12 -file /PRIVATE/PATH/development.crt
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar \
  --spring.config.additional-location=file:/PRIVATE/PATH/server.properties
```

Trust the exported public certificate explicitly in test clients, for example `curl --cacert /PRIVATE/PATH/development.crt https://127.0.0.1:8443/api`. Do not disable certificate or hostname validation. The uploader uses the JVM trust configuration; a private development CA/public-certificate truststore must be configured there if it is not already trusted. Never put a private keystore/account password in command arguments. This setup is not a deployment or permission to expose the API publicly.

## Local administration

Start the API first, then run commands against the **same data directory** from another terminal:

```sh
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar admin bootstrap owner --data-dir=/PRIVATE/PATH/library
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar admin invite --data-dir=/PRIVATE/PATH/library
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar admin users --data-dir=/PRIVATE/PATH/library --offset=0 --limit=50
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar admin sessions listener --data-dir=/PRIVATE/PATH/library --offset=0 --limit=50
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar admin revoke SESSION_ID --data-dir=/PRIVATE/PATH/library
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar admin revoke-link LINK_ID --data-dir=/PRIVATE/PATH/library
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar admin reset listener --data-dir=/PRIVATE/PATH/library
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar admin disable listener --data-dir=/PRIVATE/PATH/library
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar admin enable listener --data-dir=/PRIVATE/PATH/library
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar admin recover-owner owner --data-dir=/PRIVATE/PATH/library
```

Bootstrap/recovery prompt for password and confirmation. Non-interactive callers may use `--password-stdin` with a protected input stream; passwords are never positional arguments. Bootstrap refuses to create a second owner. Usernames are case-insensitive, normalized to lowercase, and consist of 3–64 ASCII letters/digits/dots/underscores/hyphens, starting with a letter or digit. Passwords require 12–256 Java string characters with at least 12 code points, supporting Unicode without bcrypt truncation; there is no arbitrary composition rule. They use versioned PBKDF2-HMAC-SHA256 hashes, 16-byte random salts and 600,000 iterations, through Spring Security PasswordEncoder.

Commands connect to an owner-only Unix socket derived from the canonical data-directory path: `/tmp/normalbias-admin-<path hash>/control.sock`, with directory mode **0700** and socket mode **0600**. They call the running service and its policy/transactions; they never open H2 directly. The API holds its normal exclusive data-directory lock before creating the socket. Stale socket cleanup occurs only after obtaining that ownership. Keep the API and commands under the same OS user. The socket disappears on normal shutdown; do not delete an active socket or data lock.

Invitation/reset commands return a one-time secret, link ID, kind and expiry **once**. Treat that output as a credential and share it only with the intended listener. Invitations last **24 hours**; password resets last **one hour**. A replacement reset consumes previous resets for the same account. `revoke-link` consumes a link by its non-secret ID. The shared client provides inspection and completion screens. Add `--app-origin=https://HOST:PORT` to `admin invite` or `admin reset USERNAME` to receive an additive `shareLink` field alongside the existing token/ID/kind/expiry fields. The supplied value must be a bare HTTPS origin (no trailing slash, path, userinfo, query or fragment). It is an explicit operator choice, never inferred from a request or persisted as server policy. Existing commands without the option keep their output contract.

```sh
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar admin invite \
  --data-dir=/PRIVATE/PATH/library --app-origin=https://HOST:PORT
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar admin reset listener \
  --data-dir=/PRIVATE/PATH/library --app-origin=https://HOST:PORT
```

Share only the returned link with the intended recipient. Links use `https://HOST:PORT/#invite=TOKEN` or `/#reset=TOKEN`; the fragment does not reach the HTTP server. The early browser bootstrap removes it from the current history entry before framework startup and Java consumes the transient value. Refreshing loses an uncompleted link; reopen the original link. Do not log, screenshot or persist one-time tokens. No email delivery, universal links or public account-management UI is added.

Account disable, password reset/change and local owner recovery revoke affected credentials and outstanding reset links. Re-enabling an account does not revive its revoked credentials. The owner cannot be disabled through the listener command. User/session collections are paginated (default 50, maximum 200) and omit password/token hashes. No command lists an existing raw session/upload token.

## Session and action API

Begin at **`GET /api`**. Discover named relations under `_links`; each action has `{href, method}`. Follow the returned same-origin targets and use the specified method. Server policy computes links from scope/current state and rechecks every request. A missing link is not the security boundary; directly calling a forbidden operation still fails. Do not infer authority from the displayed role.

| Endpoint | Request / result |
| --- | --- |
| `GET /api` or `/api/auth/session` | Authentication state, account/session metadata and permitted navigation/actions. Anonymous/browser responses include `csrfToken` and `csrfHeader`. No raw existing credential is returned. |
| `POST /api/auth/browser-sessions` | JSON `{username,password}` plus discovery CSRF header/cookie. Creates a persistent Secure/HttpOnly/SameSite=Lax `__Host-NormalBias-Session` cookie. Refresh discovery after login for the rotated CSRF token. |
| `POST /api/auth/device-sessions` | JSON `{username,password}`. Returns a new opaque `token`, `tokenType: Bearer`, expiry metadata and session link; creates no browser cookie. Explicit JSON credentials, not ambient-cookie authentication. |
| `DELETE /api/auth/session` | Revoke the current browser/device session and its playback access. Browser requests require the advertised CSRF header. |
| `POST /api/auth/password` | JSON `{currentPassword,newPassword}`; revoke all account credentials/reset links and require fresh sign-in. Browser requests require CSRF. |
| `POST /api/auth/invitations/inspect` | JSON `{token}`; valid unused invitation returns expiry and an `accept` relation. Missing, consumed, revoked or expired links return 404 without an accept action. |
| `POST /api/auth/invitations/accept` | JSON `{token,username,password}`; atomically create a LISTENER and consume the token. Does not sign in automatically. |
| `POST /api/auth/resets/inspect` | JSON `{token}`; valid reset returns expiry and a `complete` relation. |
| `POST /api/auth/resets/complete` | JSON `{token,password}`; atomically consume reset, replace password and revoke credentials/other reset links. Does not sign in automatically. |
| `POST /api/tracks/{id}/playback-grants` | Authenticated listening session; returns expiry and an `audio` relation whose URL carries only the scoped grant. Browser requests require CSRF. |

POST bodies for authentication endpoints use `application/json` and are limited to **16 KiB**, including chunked requests. Successful authentication/completion/grant responses currently return 200. Invalid credentials return generic **401**, insufficient scope or invalid/missing browser CSRF **403**, conflicting state/username **409**, and throttled login **429** with a conservative 900-second Retry-After. Rate limits are durable: five failed attempts per normalized username and twenty per direct peer address in a 15-minute window. Unknown and disabled accounts use the same login error. No login redirects, default password, server-generated public user or public owner-claim endpoint exists.

Browser credentials live in protected cookies; CSRF uses a separate Secure/HttpOnly cookie, with the request token/header name obtained from discovery. Bearer sessions use `Authorization: Bearer <token>` and do not depend on cookies/CSRF for their mutations. Do not put account/device credentials in query strings, localStorage or generated app assets. Authenticated discovery also advertises the uploader through an HTTP `Link` header, using the same policy as its JSON relation.

Sessions persist across app/browser and API restarts. H2 stores only SHA-256 hashes of random 256-bit opaque credentials, plus type/account/creation/last-use/revocation/expiry metadata. The server checks current account/session state on every request. Valid use extends the **90-day idle** deadline, capped by the **365-day absolute** deadline since sign-in. It never extends the latter. Browser cookie expiry is refreshed consistently; browser privacy/storage clearing may end access earlier. Expired cookies are cleared and do not block a fresh login/recovery flow. No servlet HttpSession or in-memory security context is authoritative between requests.

## Scoped playback URLs

Tracks advertise `playback-grant`; request it, then pass the returned `audio.href` unchanged to the platform media player. A grant lasts **two minutes**, is restricted to one track's **GET/HEAD audio** endpoint, allows repeated Range requests and is bound to a valid, enabled parent account/session. Only token hashes are persisted. Restart does not reset expiry or undo revocation. It grants no catalogue, artwork, upload, edit or inspection access, and works without exposing the parent credential to the media adapter.

Every new audio/HEAD/Range request revalidates grant expiry and parent validity. Logout, password changes, disable and revocation block future requests immediately. A response already opened may finish; bytes already buffered cannot be retracted. Do not assume that a player continuing buffered audio establishes that an expired grant remains valid.

Responses are `no-store` in required mode, including artwork/conditional responses, and send a no-referrer policy. Playback URLs are bearer secrets: do not log request query strings, capture them in analytics, paste them into third-party tooling or cache them in a service worker. Access logging is off by default. Grant URLs must be renewed after expiry/long pauses; the shared listening client preserves position and paused/playing intent during renewal. Server expiry/revocation checks already enforce the boundary.

Track representations advertise `self`, `artwork`, `playback-grant`, and owner-only `edit`/`delete`. Collections advertise current/next/previous links when those pages exist; album/artist rows link to their contents. The owner entry point advertises starting an inspection only when none is running, otherwise its current status link. An action advertised just before state/permission changes can become stale; handle 401/403/409 by refreshing the relevant state, not blindly replaying a mutation.

## Authenticated uploader

Create a separately revocable upload credential for the owner; write it directly to a new owner-only file so it need not appear on screen or in process arguments:

```sh
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar admin upload-token owner \
  --data-dir=/PRIVATE/PATH/library --token-out=/PRIVATE/PATH/upload.token
java -jar cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar /PATH/TO/MUSIC \
  https://127.0.0.1:8443 --token-file=/PRIVATE/PATH/upload.token
```

The JVM must trust the server certificate as described above. `--token-out` refuses an existing file. The uploader rejects symlink/non-regular or group/other-readable credential files, refuses to send a credential over HTTP, allows only literal loopback origins (no DNS/remote hosts/userinfo/paths), and never follows redirects. With a credential it discovers the advertised upload action from `GET /api` and refuses missing or cross-origin actions. Unauthenticated HTTP remains only for explicit demo mode. Authentication failure stops traversal immediately; cancellation, progress, sequential traversal and exact-file deduplication remain supported.

## Listening clients

Endpoint checks now gate credential entry with a checking/tick/cross state and six-second discovery deadline. Address edits cancel old discovery/readiness and clear the password; stale replies cannot enable sign-in. This is shared-client presentation/transport recovery only: every API request still enforces server authorisation, and successful discovery grants no permission. See the client guide for keyboard progression and actionable failure states.

The shared Java client starts with `GET /api`. Anonymous required-mode responses show login; authenticated responses open a fresh library/queue. Method-checked rooted links on the configured origin supply login, collections, artwork and grants. Redirects and foreign/ambiguous action URLs are refused before credentials are attached. Track play/enqueue requires an advertised playback-grant action. The server still enforces permission on every request. Discovery additionally advertises `artwork-revision`; groups advertise representative `artwork` links.

**Web:** serve the generated `client/target/web/` through `spring.web.resources.static-locations=file:/ABSOLUTE/PATH/client/target/web/` on the HTTPS API, then sign in with an existing account. Persistent HttpOnly/Secure/SameSite cookies restore sign-in on reload. The client never persists passwords or parent session tokens. Login rotates CSRF; discovery supplies a new masked token before mutations. Ordinary catalogue reads preserve it. Servlet session handling is disabled because H2 authentication is resolved afresh on each request; reads must not invoke a new-login CSRF strategy.

**Service selection:** the shared landing/sign-in page includes Service address, Username and Password. Native clients remember one successfully discovered HTTPS service in local preferences, ahead of the bundled default; secure credentials remain keyed by origin. Browser service selection opens the selected service’s hosted app before credentials are collected. Retry connection restores sessions without a password; connectivity failures retain existing credentials. See [client connection guidance](client-poc.md#service-connection-and-sign-in) for address validation and first-use behaviour.

**iPhone trial build default:** set `NORMALBIAS_API_ORIGIN=https://PRIVATE_IPV4:PORT`, then run `./client/build-ios-trial.sh` with Java 21. HTTPS loopback `https://127.0.0.1:PORT` is accepted for Apple's simulator. The certificate must already be trusted by the client and match the address; no TLS bypass is bundled. HTTPS builds add the Local Network description without an insecure ATS exception. The explicit resource is the initial fallback when no service has been selected locally, including when Apple's simulator reports `CN.isSimulator() == true`. Device login supplies a bearer credential for Java API/artwork calls; SecureStorage remembers it (Keychain in the signed simulator trial). Passwords and parent credentials never enter media URLs; secrets never fall back to ordinary preferences.

**Playback:** obtain a two-minute grant through the track's action before opening media. After 90 seconds since opening, resume/seek conservatively requests a fresh grant and reopens at the latest requested position, retaining queue and playing/paused intent. That local age only suggests renewal; the server decides validity. An already-open stream may continue. A failed stream gets one automatic grant/reopen attempt, keeping the last good position; repeated failure exposes Retry track. Pending seeks apply when metadata/native media is ready. A paused renewed stream stays paused.

**Logout and identity changes:** Sign out is in the full-screen Account view, reached through Profile on Library or Now Playing. It immediately releases media/system metadata, clears queue/library/artwork views, cancels polling and ignores late callbacks, then revokes the server session. Failed sign-out keeps a non-secret pending marker and a Retry sign out screen; restart retries revocation before restoring library access. A pending native credential remains only in secure storage for that revocation. Success removes the credential/cookie. API 401 clears the current session and shows login. Connectivity/5xx failures retain credentials and allow retry. Foreground discovery detects identity changes and rebuilds the library. Reauthentication never resumes an old queue or sound.

**Account setup/recovery:** browser links open Create account or Reset password after server inspection. Native login also offers **Use invitation or reset link**, accepting a complete root-page fragment link from exactly the configured HTTPS origin. Java validates the link locally before API use; tokens/passwords stay out of preferences, ordinary storage and diagnostics. Invitation acceptance asks for a username and confirmed password; reset asks for a confirmed new password. The server enforces username/password policy and one-use/expiry/revocation. An unavailable link gives one clear invalid/expired/used/revoked message. Conflicts require reinspection; ambiguous completion failures suggest trying sign-in first and never replay automatically. Submission disables duplicate submission and cancellation until its outcome returns.

If a link opens while signed in, **Sign out and continue** explicitly stops playback, clears the queue and revokes the current session before inspection; Cancel retains the existing identity/session. Successful acceptance/reset shows confirmation, then requires explicit fresh sign-in. No automatic login or playback occurs.

**Change password:** Account advertises this control only when discovery supplies `change-password`. Current password and confirmed new password are required. Cancel keeps playback/queue intact. Success stops/releases media and metadata, clears credentials/views/queue and requires sign-in. Other sessions/upload credentials and grants are invalidated by the server. Wrong-current-password 401 is distinguished from a lost session by fresh discovery. Network failures do not silently replay the mutation.

Owner bootstrap and recovery remain local operator flows above. Explicit loopback demo mode is discovered from the server and skips login/grants; it does not permit unauthenticated private-interface access.

Synthetic integration tools are in `client/tests/auth-client/`: `fixture.py` runs the real HTTPS API with temporary accounts and two original tracks under ignored `client/target/auth-client/`; `browser.cjs` uses the generated client, pins only that fixture certificate and checks real two-minute expiry. `prepare-ios.py` copies production sources into ignored output and adds test-only MCP inspection of the actual forms/player; it does not replace login/networking. This probe is never shipped in normal builds. `ios.py` exercises the signed simulator copy. Do not target the retained service or real catalogue. Actual commands, recordings and limits are in [verification](verification.md).

## Verification and remaining work

See [verification](verification.md) for actual tests and packaged commands. Flyway V5 adds authentication tables without rewriting V1–V4, catalogue metadata, audio or artwork. Existing catalogue adoption/recovery tests pass. TLS HTTP tests use generated certificates and real validation, not a trust-all client. Tests use synthetic passwords/audio only.

Account UI/share links and installed desktop Chrome restoration/cache/logout acceptance are delivered. Remaining: physical iPhone authenticated/locked playback and interruption acceptance, iPhone Home Screen PWA acceptance, and deployment/backup/restore. A fresh locked-screen/queue recording did not reproduce the earlier long silence; its cause and physical-device behaviour remain unverified. See [the feasibility guide](authenticated-audio-trial.md) for the historical result and verification.md for the new evidence. The service's whole data directory (including auth state) will need the deployment backup policy; backup/restore is not added here. No public hosting or account access for real listeners was set up.

Password-hash configuration follows [Spring Security password storage](https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html) and the PBKDF2-HMAC-SHA256 work factor in [OWASP guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). Browser request protection uses [Spring Security CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html). Spring Boot's managed versions remain pinned by the existing parent.
