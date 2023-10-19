# Installing `odinson-rest` (For Development)

## Requirements

- [docker](https://docs.docker.com/get-docker/)
- [JDK 11](https://sdkman.io/jdks#AdoptOpenJDK)
- [sbt](https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Linux.html)
- 2G of RAM

## Install

Clone the [odinson-rest](https://github.com/lum-ai/odinson-rest) repository.

## Python library

### Build

```bash
cd python
pip install -e ".[all]"
```

## REST API (Scala)
### Build

The project that defines the REST API can be built using either docker or sbt; however, the recommended method is to use docker.

#### Docker

We construct our docker images using the sbt [native-packager](https://www.scala-sbt.org/sbt-native-packager/formats/docker.html) plugin:

```scala
sbt dockerize
```

For information on additional tasks (generating Dockerfiles, publishing images, etc.), see [this section of the `native-packager` documentation](https://www.scala-sbt.org/sbt-native-packager/formats/docker.html#tasks).

##### Building docker images for other architectures

```bash
sbt ";clean; docker:stage"
cd target/docker/stage
docker buildx build --platform=linux/amd64 -o type=docker -t "lumai/odinson-rest-api:amd64" .
```

#### `sbt`

The REST API server can be launched directly using SBT:

```scala
sbt web
```

### Running

To run the Odinson REST API in development mode, run the following command:

```bash
sbt web
```

Open your browser to [localhost:9000](http://localhost:9000).