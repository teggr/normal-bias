# normal-bias
A self hosted music service

## Project documentation

- [Project brief and agreed constraints](docs/project-brief.md)
- [Instructions for coding agents](AGENTS.md)
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

Read the [local PoC guide](docs/local-poc.md) for endpoints, seeking examples, tests and limitations. This unauthenticated local PoC must not be exposed publicly. No deployment or listening client is included.
