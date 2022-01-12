# REST API

The reader can be used through the REST API. After building the docker image, launch a container using the following command:

```bash
docker run --name="cgiar-reader" \
  -it \
  --restart "on-error" \
  -e "HOME=/app" \
  -p "0.0.0.0:9000:9000" \
  --rm \
  "lumai/cgiar-reader-rest-api:latest"
```

Navigate to [localhost:9000/api](http://localhost:9000/api) to interactively explore the API through the [OpenAPI 3.0](http://spec.openapis.org/oas/v3.0.3) specification.

## API Endpoints and Examples

The main endpoint is `/api/extract`, which returns a json file of extracted mentions over the query.

| Endpoint | Example |
| :--- | :--- |
| /api/extract | [Github Gist](https://gist.github.com/myedibleenso/9241a4c9c71d29f148ef0b8c44602b60) |
| /api/annotate | [Github Gist](https://gist.github.com/zwellington/21688441b3d8a62f8e2f2051e1792a63) |
| /api/taxonomy/hyponyms-for | [Github Gist](https://gist.github.com/zwellington/7a7ae44bff5cd890198d2eea4f2f0145) |
| /api/taxonomy/hypernyms-for | [Github Gist](https://gist.github.com/zwellington/59041ebd68e60e1b7f21bb30545a4213) |

## Common Workflows

- ??

# sbt

Several `command aliases` are defined in the `build.sbt`. These can be altered and added to at the developers discretion.

- `copyGrammars`: Cleans and edits the source grammars to match the user-defined top level grammars.

- `cleanTest`: Runs `copyGrammars` then `test`. This runs the tests on "clean" grammars.

- `dockerize`: Command for generating and publishing a docker image on the "clean" grammars.

- `documentize`: Generates `scaladoc` and copies documentation to the `docs/` directory.

<!--- 
- Manipulating mentions
  - python
  - scala
-->
