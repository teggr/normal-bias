# normal-bias
A self hosted music service

## Project documentation

- [Canonical application design reference](DESIGN.md)
- [Historical Graphite design proposal](docs/design-system-proposal.md)
- [Project brief and agreed constraints](docs/project-brief.md)
- [Authentication plan and progress](docs/authentication-plan.md)
- [Server authentication and local administration](docs/authentication.md)
- [Authenticated iOS simulator audio feasibility](docs/authenticated-audio-trial.md)
- [Web listening-client PoC](docs/client-poc.md)
- [Album matching and missing artwork](docs/album-artwork.md)
- [Desktop simulator demo](docs/simulator-poc.md)
- [Local device trial plan](docs/local-device-trial.md)
- [CarPlay feasibility PoC and signing gate](docs/carplay-poc.md)
- [Instructions for coding agents](AGENTS.md)
- [Official Codename One authoring skill](.agent-skills/codename-one/SKILL.md)
- [Cloud UI demonstration recording skill](.agents/skills/ui-demo-recording/SKILL.md)
- [Storage migrations, recovery and online inspection](docs/storage-reliability.md)

The local PoC includes a Spring Boot API, embedded H2 catalogue, local MP3 storage and a Java directory uploader. The API supports paginated song, album and artist views, listing/search, catalogue metadata editing and track deletion. Owner/listener accounts, persistent sessions and scoped playback URLs are implemented, with invitation/reset links, password change, login, remembered sessions, logout and playback URL renewal integrated in the web/iPhone client. Java 21 JDK and Maven 3.6.3+ are required.

The API requires authentication and HTTPS by default. See [authentication](docs/authentication.md) for TLS, owner setup and upload credentials. The web and iPhone clients support sign-in and scoped playback; see that guide for the private HTTPS flow. The unified sign-in page also lets you select a service: native clients remember its HTTPS address, while browsers open the selected service’s hosted app. See [service connection guidance](docs/client-poc.md#service-connection-and-sign-in). For an unauthenticated synthetic local demo, explicitly select loopback-only demo mode:

```sh
mvn clean verify
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar --normalbias.auth.mode=demo
# In another terminal; start with the bundled synthetic fixture:
java -jar cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar api/src/test/resources
curl http://127.0.0.1:8080/api/tracks
```

The API listens on `127.0.0.1:8080` and stores its catalogue and music under `./data` relative to the launch directory. Ctrl-C cancels the uploader; rerunning retries whole files and deduplicates identical content.

Startup applies versioned schema migrations and recovers prepared uploads and committed deletions. Start a background inspection while it runs with `curl -X POST 'http://127.0.0.1:8080/api/inspections?mode=full'`. Follow the returned Location for status and append `/findings` for paginated results. Backup/restore remains part of the future deployment story.

Read the [local PoC guide](docs/local-poc.md) for endpoints, seeking examples, tests and limitations. Demo mode is unauthenticated and refuses non-loopback binds. No public deployment is included. The optional Codename One web/iPhone client now supports login, session restoration, logout and scoped playback renewal. Installed desktop Chrome session/cache acceptance passes; physical authenticated iPhone acceptance remains open.

## Web listening client

With the Java 21 JDK, Maven, Python 3, Bash and `unzip` available:

```sh
./client/build-web.sh
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar \
  --normalbias.auth.mode=demo \
  --spring.web.resources.static-locations="file:$(pwd)/client/target/web/"
```

Open `http://127.0.0.1:8080/` to browse All songs, Albums or Artists with continuous scrolling and an A–Z / # jump rail, search, play, seek and queue tracks. Albums opened directly or through Artists show artwork/title with Close and Queue album controls. The first queue addition prepares its first track paused for Play; Clear unloads it, and the mini-player offers play/pause and Next. Library and Now Playing are separate destinations with a compact toolbar; Queue opens from its dedicated player button, with Back to Now Playing and a one-shot Shuffle action; the player also offers Shuffle in its bottom-right control, and Profile opens full-screen Account options with Back to the launching view. Build the API first with the command above, and stop any API already using the same data directory before relaunching it. The separate client build translates Java locally; it requires no cloud build account. See the [client guide](docs/client-poc.md) for the browser audio bridge, testing commands and limitations. The separate [local iPhone trial](docs/local-device-trial.md) now has a local signing/install route, private development origin and framework media adapter. The service automatically fills missing covers in the background using MusicBrainz and Cover Art Archive, without matching controls in the listening app or rewriting MP3s. See the [artwork guide](docs/album-artwork.md). The prior native increment preserves iPhone audio across backgrounding and displays embedded ID3 covers from the API; physical acceptance and platform limits are tracked in [verification](docs/verification.md).

## Simulator demo

With Java 21, Maven, Bash, FFmpeg/FFprobe, curl and desktop sound available:

```sh
./client/demo-simulator.sh
```

This prepares a disposable local library with two synthetic tracks and launches the Codename One desktop simulator. No cloud account or signing is needed. See the [simulator guide](docs/simulator-poc.md) for connecting to an existing API, proxy troubleshooting and verification limits.

## Progress and next steps

The API and shared client support authenticated browsing, account setup/recovery, session restoration and playback-grant renewal. Library now adds bounded continuous scrolling and alphabetical navigation, with its approved mobile concept saved in [DESIGN.md](DESIGN.md). Build/test commands, dated results, recordings and platform limits are recorded in [verification](docs/verification.md).

Remaining: physical iPhone authentication/locked playback/interruption acceptance, then separately authorised retained-service migration and deployment/backup work. The signed phone build is ready; Robin subsequently selected the iPhone simulator instead of physical-device acceptance, so it was not installed. The retained phone app, library/service and public hosting were not changed.

See the [current project checkpoint](docs/project-brief.md#current-library-play-checkpoint--2026-11-25) and [authentication plan](docs/authentication-plan.md) for delivered scope and outstanding work. The [album matching proposal](docs/album-matching-proposal.md) remains historical where superseded by automatic background artwork filling in [the artwork guide](docs/album-artwork.md).
