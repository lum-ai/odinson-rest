# Install

## Requirements

- [sbt](https://www.scala-sbt.org/)
- [docker](https://docs.docker.com/get-docker/)
- >= G of RAM

## REST API

<!-- ### Releases

We publish releases in the form of docker images:

- ?? -->

### Build

The project can be built using either docker or sbt; however, the recommended method is to use docker.

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

#### `sbt` (Scala)

The REST API server can be launched directly using SBT:

```scala
sbt web
```
