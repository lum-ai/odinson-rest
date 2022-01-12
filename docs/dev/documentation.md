# Documentation

You can view the latest documentation at the [`cgiar-reader Website`](https://cgiar-reader.github.io/cgiar-reader/).

## API Documentation

We use `scaladoc` to generate our API documentation. To generate API documentaion use the following command:

```bash
sbt doc
```

This will generate HTML pages documenting the API for each subproject:

- `reader`: reader/target/scala-2.12/api/index.html
- `rest`: rest/target/scala-2.12/api/index.html

!!! note
    These files are copied to the `docs/api` directory when the command `sbt documentize` is used.

## General Documentation

We use `mkdocs` to generate our site documentation from markdown. Markdown source files are located under the `docs` directory. To develop the documentation with live updates use the following command:

```bash
docker run --rm -it -v $PWD:/app \
    -p 8000:8000 \
    parsertongue/mkdocs:latest \
    mkdocs serve -a 0.0.0.0:8000
```

Open your browser to [localhost:8000](http://localhost:80000).