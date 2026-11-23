# Authentication plan

Updated 2026-11-23 after Robin settled the product decisions, authorised the bounded native-audio check, then requested implementation starting with server accounts/sessions and scoped playback URLs. The server foundation is implemented as described in [the operating/API guide](authentication.md). Robin subsequently authorised web/iPhone login, remembered sessions, logout and scoped playback renewal, now integrated in shared Java code. Public deployment remains separate.

## Server and client milestones delivered — 2026-11-23

Implemented: Flyway V5 owner/listener accounts; versioned PBKDF2-HMAC-SHA256 password hashes; H2-backed browser/device/upload credentials with 90-day idle/365-day absolute expiry; CSRF-protected browser endpoints; generic/throttled login; one-use 24-hour invitations and one-hour resets; password/session/link revocation; and two-minute track-scoped playback URLs tied to parent validity. Local administration uses a 0700/0600 Unix socket owned by the running service, preserving exclusive H2 ownership. The uploader reads an owner-only credential file over loopback HTTPS, follows the advertised upload relation and refuses cross-origin targets.

Security is required by default; plain API HTTP returns 426. Explicit legacy demo mode is literal-loopback-only. Scope/state-dependent links are additive to current track/group/page data, and server enforcement revalidates requests. Inspection start/status affordances follow running state. TLS tests, persistent restart/expiry/race/revocation checks, packaged commands and legacy demo-browser regressions are recorded in verification.md.

Client discovery/action links, login, remembered cookie/Keychain sessions, logout and position/intent-preserving playback renewal are integrated. Remaining: invitation/reset/password-change screens, app-facing share links, installed-PWA acceptance, and physical locked/background audio acceptance. The prior simulator sound gap remains unresolved. No retained service/phone or real-library migration was performed.

## Intended outcome and decisions

One owner and invited listeners sign in to a shared library in the browser and iPhone app. Listeners can browse, search and play with their own session-local queue. Only the owner manages music and storage; the uploader imports using a separate revocable upload credential. Unauthenticated requests cannot access catalogue metadata, artwork, audio, mutations or storage inspection. Preserve Range streaming, seeking, background playback and queue completion.

Keep Java/Maven, Spring Boot, H2, the single-instance service, Codename One 7.0.282 and browser audio bridge. First verify against a private HTTPS endpoint; public VPS deployment remains separate. The current private HTTP phone trial must not carry the new real passwords or remembered credentials. Configure trusted development TLS for web/iPhone without disabling certificate validation; coordinate any change to the retained phone/service.

Agreed decisions:

- Audience: one owner and invited listen-only users sharing one library. No per-user private libraries or listener upload/edit/delete rights.
- Sign-in: local username/password; no external identity provider or public registration.
- Invitations: owner generates single-use, expiring invitation links and shares them personally. Recipients choose their username/password; no email delivery service.
- Recovery: owner generates listener reset links; a local server command recovers the owner's account.
- Administration: local server commands manage invitations, accounts and credential/session revocation. No account-administration UI in web/iPhone; clients still provide sign-in, invitation/reset completion, password change and logout.
- Persistence: web and iPhone remain signed in across app/browser and service restarts, expiring after 90 inactive days or one year since password sign-in, whichever comes first. Ordinary use extends the idle deadline but never the absolute deadline. Browser/device storage deletion can end sign-in earlier.
- Revocation: reject subsequent requests immediately, including later Range requests, but let an already-open response finish. Logout stops/releases the local player immediately. No promise to retract buffered audio or cancel active server streams.
- Rollout/uploader: private HTTPS trial first; uploader remains loopback-only with an owner-issued upload credential. Public deployment and remote uploads are excluded.

The existing finite queue remains session-local and is not synchronised between users/devices. Moving it server-side or adding persistent playlists is separate scope.

## Server authority and HATEOAS

The server is the source of truth for users, roles/scopes, invitation/reset lifecycle, credential/session validity, expiry and permitted state transitions. Persist authoritative authentication state in H2 so restart does not reset expiry, revive a consumed invitation or undo revocation. Clients keep only necessary credentials, presentation state, media execution and bounded caches; local clocks/role checks cannot grant access.

New authentication/session representations and touched library resources advertise named links/action affordances for the currently authenticated principal and resource state. Use stable relations and action descriptors that identify the target and HTTP method. Clients discover these from a fixed API entry point, follow the returned relations and render server-backed controls from advertised actions. Credentials may only be sent to the configured trusted API origin; do not follow arbitrary cross-origin action targets.

Examples: the entry/session response exposes sign-in when anonymous and sign-out/change-password/library navigation when authenticated; track resources expose playback/artwork access to listeners, and edit/delete only to an owner when the operation is valid. Invitation/reset completion is advertised only after a valid, unused, unexpired token has been checked. Account administration remains local-command-only: do not invent public invitation-management endpoints or return administrative actions to clients.

Compute affordances from the same authorisation/state policy used to enforce requests. Missing links are a UI contract, not a security boundary: direct or stale requests must still be rejected server-side. Handle 401 (reauthentication), 403 (permission denied) and state conflicts by refreshing the relevant representation; never retry a mutation blindly. Invitation/reset consumption and state changes must be checked atomically.

Introduce links/actions additively on the entry/session responses and library resources used by this increment. Preserve existing collection pagination fields and track data; document compatibility changes and test the Java client/CLI consumers. Do not rewrite every existing API or convert all playback mechanics into remote calls as part of authentication.

## Architecture and remaining client work

The server uses Spring Security 6.5.11 from the existing Spring Boot 3.5.16 dependency set, without upgrading Boot or Codename One. The login/session/logout/grant client flow is implemented; invitation/reset/password-change UI remains future work.

**Browser:** durable server-side sessions with persistent HttpOnly/Secure cookies under HTTPS and an appropriate SameSite policy, keeping API and client same-origin. Persist session validity/idle/absolute deadlines in H2 and keep cookie expiry consistent with the agreed lifetime. Retain CSRF protection for cookie-authenticated mutations, including login/logout. API failures return structured 401/403 responses rather than HTML redirects. Public access is limited to shell assets and authentication bootstrap/invitation/reset flows; it does not expose library data. No browser credentials in localStorage or generated assets.

**Identity and administration:** implemented through Spring Security PasswordEncoder (versioned PBKDF2-HMAC-SHA256, 600,000 iterations) and local commands over an owner-only Unix socket to the running service. No default password, public first-user claim or competing H2 writer. Secrets are supplied through protected stdin/prompts/files, not command arguments/logs. Flyway V5 adds hashes and durable state without editing applied migrations. Invitation/reset consumption is atomic. Reset/change/recovery invalidate account credentials and outstanding reset links; fresh sign-in is required. Invitation/reset lifetimes are 24 hours/one hour. Exact commands are in the authentication guide.

**iPhone:** uses revocable opaque device credentials in Authorization headers for API/artwork calls, with hashed token storage and authoritative lifetime checks server-side. Persist credentials using verified Keychain-backed storage, not ordinary preferences. Local inspection found SecureStorage non-prompting APIs and a StreamRequest header API in pinned 7.0.282; scoped URLs supply native media; physical-device/locked-output acceptance remains separate. Separate connectivity failures from authentication failures, and never prompt for login from a background callback.

**Uploader:** separately revocable upload-scoped credentials supplied through a protected file, standard input or secure prompt. Avoid secrets in URLs/command arguments; stop promptly on authentication failure. Preserve cancellation, deduplication, progress and loopback destination restrictions. Remote HTTPS uploads require a separate scope decision.

## First technical gate: authenticated media

LibraryApi constructs direct audio URLs. Browser glue passes them to HTMLAudioElement; FrameworkAudio passes them to MediaManager.createMediaAsync. API request headers cannot be assumed to reach these transports.

Before implementing the full login feature, use a disposable synthetic library to:

1. Prove browser same-origin cookies cover API/audio requests, HEAD/Range, seeking and track advance.
2. Inspect the pinned iPhone media implementation and prove secure cookie/header propagation while foregrounded, locked and backgrounded, including a new track and later Range requests.
3. If that fails, evaluate bounded native adaptation against short-lived, track-scoped playback grants. A URL grant is a bearer secret requiring expiry, revocation, log redaction and GET/HEAD-only scope. It must allow repeated Range requests. Never put the account/device credential in an audio URL.
4. Enforce the agreed revocation policy for every new request, including playback-grant requests and later Range requests. A grant must not outlive its parent session's validity/revocation. Permit an already-open response to finish; logout stops local media. Test server-computed expiry rather than relying on client clocks.

The simulator gate below established the scoped-URL approach now implemented server-side. Browser/simulator success does not establish physical iPhone compatibility.

### Simulator feasibility result — 2026-11-23

Robin selected Apple's iPhone simulator instead of the physical device for this session. Pinned iOS stream headers are unsupported, confirmed by bytecode and runtime rejection. The isolated real-client trial passed authenticated playback using server-issued, two-minute, track-scoped URL grants, playing/paused seeks, fresh Range requests and completion-driven grant acquisition/queue advance while Settings was foregrounded. Ad hoc simulator signing enabled SecureStorage roundtrip and credential restoration after app relaunch. Fifteen gate checks and eight native evidence assertions pass. See [the trial guide](authenticated-audio-trial.md) and [verification](verification.md) for actual commands and results.

Use scoped media links as the demonstrated simulator route when designing the authentication increment; parent credentials must remain out of media URLs. Define renewal after long pauses/expiry with position and paused/playing-intent preservation. Browser authentication and physical iPhone acceptance remain open. The simulator's playback clock advanced while locked, but the saved capture has an unexplained roughly 13-second silent interval during lock-screen interaction; uninterrupted locked output is not established. This probe does not implement durable production sessions, account administration or the 90-day/one-year policies.

## Delivery sequence

1. Completed at simulator level: authenticated media/secure-storage feasibility and trusted private HTTPS. Physical locked audio remains open; invitations/resets now last 24 hours/one hour, and local commands use a protected Unix socket.
2. Completed server foundation: durable identity/sessions, administration, invitation/reset state, scope/state policy and HATEOAS links, CSRF/throttled generic login, expiry/revocation and scoped media URLs. The authenticated uploader follows advertised actions and uses a protected credential file.
3. Integrated: link-driven login, session restoration, logout/401 handling and scoped playback renewal. Invitation/reset/password-change UI remains next. Clear personal caches and the local queue, release media and ignore stale callbacks on logout/account change. Do not automatically resume sound after reauthentication; preserve the queue through recoverable connection failures and never share it across accounts.
4. Browser and iPhone simulator acceptance passed for the integrated login/session/logout/renewal flows, including real grant expiry and background queue advance. Installed-PWA and physical-device/locked/interruption acceptance remain open. Coordinate retained phone/service updates only when explicitly requested. Operator setup/recovery/compatibility are documented for the server. Required authentication is already the default; demo mode remains explicit and loopback-only. Public deployment and personal-library migration are not implicit.

## Acceptance status and remaining verification

- Unauthenticated access is denied for all catalogue/group/search, artwork/audio (including HEAD/Range/conditional requests), upload/edit/delete, inspection and retained artwork-management routes.
- Wrong passwords, invalid/expired/revoked credentials, missing CSRF tokens, insufficient scope, fixation, throttling and reset invalidation have meaningful tests.
- Owner/listener/upload scopes produce the correct links/actions. Listener representations omit management actions, and direct management requests are denied. State changes remove invalid actions; stale links cannot bypass enforcement. Clients follow changed server-provided action targets rather than construct their own URLs or infer permission from role names.
- Concurrent invitation/reset redemption succeeds once only; consumed/expired/revoked links remain unusable after restart. Account disable/revocation affects new requests while an already-open stream can finish.
- Browser/device sign-ins survive API restarts; synthetic clocks verify 90-day idle and one-year absolute expiry, including activity that extends only the idle deadline. Clients cannot override expiry decisions.
- Browser login/reload/logout/expiry work. Shared caches and service workers do not leak private responses; check installed-PWA behaviour as well as fresh browser contexts.
- Physical iPhone checks cover sign-in persistence, artwork, seeking, locked/background next-track advance, reconnect, expiry and revocation.
- Uploader credential failures, scope, cancellation and deduplication work. Existing catalogue IDs/audio/artwork remain unchanged.
- Completed build checks: Java 21 `mvn -B -ntp verify` (81 API + 5 CLI tests), `./client/build-web.sh` (20 client tests), real browser authentication/recovery and demo smoke checks, and local signed iPhone simulator checks. The exact commands/results and physical/PWA gaps are in [verification](verification.md).
- Delivered: verified synthetic web/native UI recordings with actual client sound, including paused seeking, renewed playback and logout silence. Saved-artifact playback/audio checks and local paths are in [verification](verification.md). Physical-device and installed-PWA recordings remain separate acceptance work.

## Sources reviewed

- [Spring Security 6.5 sessions](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/session-management.html)
- [Spring Security 6.5 CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)
- [Spring Security 6.5 password storage](https://docs.spring.io/spring-security/reference/6.5/features/authentication/password-storage.html)
- [OWASP session management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)

These guide the proposal; they do not establish untested client compatibility or authorize implementation.
