# artemis-feature-model

An interactive feature model for [Artemis](https://github.com/ls1intum/Artemis). The Spring Boot and Angular
application shows which features Artemis offers and how they depend on each other, checks a selection against the
model's constraints, and turns a valid selection into configuration files and deployment packages. An extraction
pipeline builds the model from the Artemis source code, and GitHub workflows deliver it as a container image.

## Documentation

The documentation site is published at <https://ls1intum.github.io/artemis-feature-model/>:

- The [Developer Guide](documentation/docs/developer/intro.mdx) is for people who change the code of this
  repository: setup, conventions, architecture, modules, and the REST API.
- The [Maintainer Guide](documentation/docs/maintainer/intro.mdx) is for people who keep the feature model in line
  with Artemis: the extraction pipeline, the delivery workflows, and step-by-step maintenance tasks.

The sources of the site live in [`documentation/`](documentation/README.md).

## Quick start

You need Java 25 and Node.js 24 (`>=24.7.0 <25`, with npm `>=11.5.1`). The Gradle wrapper downloads Gradle itself.

```bash
./gradlew bootRun     # server on http://localhost:8090
npm install
npm run start         # client on http://localhost:9090
```

Open `http://localhost:9090`. The start page is the **Configurator**; the **Explorer** is one click away. The client
forwards every request below `/api` to the server. The ports differ from the Artemis development defaults (8080 and
9000), so you can run both applications side by side.

## Common commands

```bash
./gradlew test                    # server tests
npm run test -- --watch=false     # client tests, one run
./gradlew bootJar                 # client and server in one runnable jar under build/libs
```

The [Developer Guide setup](documentation/docs/developer/setup.mdx) explains these commands and common problems.

## License

MIT, see [`LICENSE`](LICENSE).
