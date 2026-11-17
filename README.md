# normal-bias
A self hosted music service

## Project documentation

- [Project brief and agreed constraints](docs/project-brief.md)
- [Instructions for coding agents](AGENTS.md)

The first local PoC includes a Spring Boot API, embedded H2 catalogue, local MP3 storage and a Java directory uploader. Java 21 JDK and Maven 3.6.3+ are required.

```sh
mvn clean verify
java -jar api/target/normal-bias-api-0.1.0-SNAPSHOT.jar
# In another terminal; start with the bundled synthetic fixture:
java -jar cli/target/normal-bias-cli-0.1.0-SNAPSHOT.jar api/src/test/resources
curl http://127.0.0.1:8080/api/tracks
```

The API listens on `127.0.0.1:8080` and stores its catalogue and music under `./data` relative to the launch directory. Ctrl-C cancels the uploader; rerunning retries whole files and deduplicates identical content.

Read the [local PoC guide](docs/local-poc.md) for endpoints, seeking examples, tests and limitations. This unauthenticated local PoC must not be exposed publicly. No deployment or listening client is included.
