# normal-bias
A self hosted music service

## Project documentation

- [Graphite client design system and implementation](docs/design-system-proposal.md)
- [Project brief and agreed constraints](docs/project-brief.md)
- [Web listening-client PoC](docs/client-poc.md)
- [Desktop simulator demo](docs/simulator-poc.md)
- [Local device trial plan](docs/local-device-trial.md)
- [Instructions for coding agents](AGENTS.md)
- [Official Codename One authoring skill](.agent-skills/codename-one/SKILL.md)
- [Cloud UI demonstration recording skill](.agents/skills/ui-demo-recording/SKILL.md)
- [Storage migrations, recovery and online inspection](docs/storage-reliability.md)

The local PoC includes a Spring Boot API, embedded H2 catalogue, local MP3 storage and a Java directory uploader. The API supports paginated listing/search, catalogue metadata editing and track deletion. Java 21 JDK and Maven 3.6.3+ are required.

```sh
mvn clean verify
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar
# In another terminal; start with the bundled synthetic fixture:
java -jar cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar api/src/test/resources
curl http://127.0.0.1:8080/api/tracks
```

The API listens on `127.0.0.1:8080` and stores its catalogue and music under `./data` relative to the launch directory. Ctrl-C cancels the uploader; rerunning retries whole files and deduplicates identical content.

Startup applies versioned schema migrations and recovers prepared uploads and committed deletions. Start a background inspection while it runs with `curl -X POST 'http://127.0.0.1:8080/api/inspections?mode=full'`. Follow the returned Location for status and append `/findings` for paginated results. Backup/restore remains part of the future deployment story.

Read the [local PoC guide](docs/local-poc.md) for endpoints, seeking examples, tests and limitations. This unauthenticated local PoC must not be exposed publicly. No deployment is included. An optional Codename One web listening client now uses this API.

## Web listening client

With the Java 21 JDK, Maven, Bash and `unzip` available:

```sh
./client/build-web.sh
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar \
  --spring.web.resources.static-locations="file:$(pwd)/client/target/web/"
```

Open `http://127.0.0.1:8080/` to browse/search, play, seek and queue tracks. Build the API first with the command above, and stop any API already using the same data directory before relaunching it. The separate client build translates Java locally; it requires no cloud build account. See the [client guide](docs/client-poc.md) for the browser audio bridge, testing commands and limitations. The separate [local iPhone trial](docs/local-device-trial.md) now has a local signing/install route, private development origin and framework media adapter. The latest increment preserves iPhone audio across backgrounding and displays embedded ID3 covers from the API; physical acceptance and platform limits are tracked in [verification](docs/verification.md).

## Simulator demo

With Java 21, Maven, Bash, FFmpeg/FFprobe, curl and desktop sound available:

```sh
./client/demo-simulator.sh
```

This prepares a disposable local library with two synthetic tracks and launches the Codename One desktop simulator. No cloud account or signing is needed. See the [simulator guide](docs/simulator-poc.md) for connecting to an existing API, proxy troubleshooting and verification limits.

## Progress and next steps

See the [project brief](docs/project-brief.md#current-progress-and-next-proposal--2026-11-20) for the completed mobile/audio/artwork checkpoint and [verification](docs/verification.md) for actual checks. The [album matching and missing artwork proposal](docs/album-matching-proposal.md) tracks proposed milestones for identifying albums, previewing/accepting fetched covers, persistent artwork and later bulk backfill. It is a proposal; automatic external artwork lookup is not implemented.
