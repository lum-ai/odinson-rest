# REST API

The reader can be used through the REST API. After building the docker image, launch a container using the following command:

<!-- ```bash
docker run --name="odinson-rest" \
  -it \
  --restart "on-error" \
  -e "HOME=/app" \
  -p "0.0.0.0:9000:9000" \
  --rm \
  "lumai/odinson-rest-api:latest"
``` -->

Navigate to [localhost:9000/api](http://localhost:9000/api) to interactively explore the API through the [OpenAPI 3.0](http://spec.openapis.org/oas/v3.0.3) specification.

## Python library

We also provide a Python library as a simple way to build applications that interact with the Odinson REST API.  You can either connect to an existing odinson-rest service or launch one using docker.

### Launching and interacting with a service using docker

```python
from lum.odinson.doc import Document, Fields
from lum.odinson.rest.docker import DockerBasedOdinsonAPI

# create a local index
data_dir = "/local/path/to/my/data/dir"
engine = DockerBasedOdinsonAPI(local_path=data_dir)

# load an Odinson document
doc_file = "path/to/odinson/document.json"
doc = Document.from_file(doc_file)

# index the document
engine.index(doc)

# query the index
for res in engine.search(odinson_query="[lemma=be]"):
  for span in res.spans():
    print(f"{res.document_id} ({res.sentence_index}):  {span}")
```
### Validating a rule


```python
from lum.odinson.rest.docker import DockerBasedOdinsonAPI

engine = DockerBasedOdinsonAPI()
# will return False
engine.validate_rule("[")
# will return True
engine.validate_rule("[word=Gonzo]")

engine.close()
```

<!-- ## API Endpoints and Examples

The main endpoint is `/api/extract`, which returns a json file of extracted mentions over the query.

| Endpoint | Example |
| :--- | :--- |
| /api/extract | [Github Gist](https://gist.github.com/myedibleenso/9241a4c9c71d29f148ef0b8c44602b60) |
| /api/annotate | [Github Gist](https://gist.github.com/zwellington/21688441b3d8a62f8e2f2051e1792a63) |
| /api/taxonomy/hyponyms-for | [Github Gist](https://gist.github.com/zwellington/7a7ae44bff5cd890198d2eea4f2f0145) |
| /api/taxonomy/hypernyms-for | [Github Gist](https://gist.github.com/zwellington/59041ebd68e60e1b7f21bb30545a4213) | -->

## Common Workflows

- ??

# sbt

Several `command aliases` are defined in the `build.sbt`. These can be altered and added to at the developers discretion.

- `dockerfile`: Command for generating and publishing a docker image (`target/docker/stage`).
- 
- `dockerize`: Command for generating and publishing a docker image.

- `documentize`: Generates `scaladoc` and copies documentation to the `docs/` directory.

<!--- 
- Manipulating mentions
  - python
  - scala
-->
