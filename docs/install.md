# Install

## Requirements

- [sbt](https://www.scala-sbt.org/)
- [docker](https://docs.docker.com/get-docker/)
- $\ge$ 2G of RAM


## Python library

### install from GitHub
```bash
pip install -U "odinson-rest[all] @ git+https://github.com/lum-ai/odinson-rest@main#subdirectory=python"
```
## REST API

<!-- ### Releases

We publish releases in the form of docker images:

- ?? -->

```bash
# NOTE: replace <local/path/to/data> with the absolute path
# to the location you want data to be written to on your machine
docker run -it -p "8000:8000" -v "<local/path/to/data>:/app/data" "lumai/odinson-rest:latest"
```
