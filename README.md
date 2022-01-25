# odinson-rest

This project provides a REST API for Odinson, as well as a [Python library for easily interacting with this REST API](./python).

Using the REST interface, you can ...

- **Validate** Odinson documents
- **Index** Odinson documents
- **Delete** indexed Odinson documents
- **Update** indexed Odinson documents
- **Search** over a collection of Odinson documents
- **Retrieve** documents and their metadata

The [Python library](./python) provides an easy way of manipulating Odinson documents from Python.
## Requirements

- [sbt](https://sdkman.io/sdks#sbt)
- [docker](https://docs.docker.com/get-docker)

For details, [see the docs](https://lum-ai.github.io/odinson-rest).

## Testing

You can run all project tests with the following command:

```scala
sbt test
```

## Documentation

You can generate API documentation using the following command:

```scala
sbt doc
```

This will generate HTML pages documenting the API:

- `rest`: target/scala-2.12/api/index.html

## REST API

### Releases

We publish releases in the form of multiplatform docker images:
- DockerHub: [`lumai/odinson-rest-api`](https://hub.docker.com/r/lumai/odinson-rest-api)

### Build

The REST API is meant to be run via Docker.  Images are built using an SBT task.

#### Docker

We construct our docker images using the sbt [native-packager](https://www.scala-sbt.org/sbt-native-packager/formats/docker.html) plugin:

```scala
sbt dockerize
```

For information on additional tasks (generating Dockerfiles, publishing images, etc.), see [this section of the `native-packager` documentation](https://www.scala-sbt.org/sbt-native-packager/formats/docker.html#tasks).

#### `sbt` (Scala)

The REST API server can be launched directly using SBT:

```scala
sbt web
```

### Run

After building the docker image, launch a container using the following command:

```docker
docker run --name="odinson-rest-api" \
  -it \
  --restart "on-failure" \
  -e "HOME=/app" \
  -p "0.0.0.0:9000:9000" \
  "lumai/odinson-rest-api:latest"
```

Navigate to [localhost:9000/api](http://localhost:9000/api) to interactively explore the API through the [OpenAPI 3.0](http://spec.openapis.org/oas/v3.0.3) specification.


## Develop

```bash
sbt web
```

## Support

For feature requests and bug reports, please open an issue.

## Citing

Please see [CITATION.cff](./CITATION.cff)

## Authors

- [Gus Hahn-Powell](https://parsertongue.org/about)
- [Dane Bell](http://danebell.info)
