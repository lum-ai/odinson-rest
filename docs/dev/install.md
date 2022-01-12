# Installing `cgiar-reader` (For Development)

## Requirements

- [docker](https://docs.docker.com/get-docker/)
- [JDK 11](https://sdkman.io/jdks#AdoptOpenJDK)
- [sbt](https://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Linux.html)
- 8G of RAM

## Install

Clone the [cgiar-reader](https://github.com/cgiar-reader/cgiar-reader) repository.

!!! note
    We suggest developing the `cgiar-reader` on a Linux environment under the `~/repos/cgiar-reader` directory. This documentation contains commands which run under these assumptions.

<!--- FIXME: docker run command for odin-tutorial image -->
To run the visualizer alongside the cgiar-reader REST API, clone the [odin-tuorial](https://github.com/cgiar-reader/odin-tutorial) repository and change the `docker-compose.yml` file to:

```yaml
version: "3.8"
services:
  frontend:
    image: parsertongue/odin-tutorial:local
    build:
      context: .
      dockerfile: Dockerfile
    restart: unless-stopped
    ports:
      - "8880:7777"
    env_file:
      - ./.env
    environment:
      ODIN_API_BASE_URL: http://0.0.0.0:9000/api
```

## Running

To run the cgiar-reader REST API in development mode, run the following command under the `cgiar-reader` directory to redirect to the external grammars:

```bash
RULES_PREFIX=file://$HOME/repos/cgiar-reader/cgiar-reader/reader/grammars/cgiar sbt web
```

Open your browser to [localhost:9000](http://localhost:9000).

!!! note
    If you didn't clone the `cgiar-reader` repository under the recomended directory, change the `RULES_PREFIX` path to reflect your local path.

To run the visualizer *without docker*, you'll need a version of Node installed.  Clone the `odin-tutorial` repo run the command from the project root:

```bash
npm install && npm run start
```

Open your browser to [localhost:7777/playground](http://localhost:7777/playground).
