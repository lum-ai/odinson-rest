from typing import List, Text


class AppInfo:
    """
    General information about the application.

    ***This repo was generated from a cookiecutter template published by myedibleenso and zwellington.
    See https://github.com/clu-ling/clu-template for more info.
    """

    version: Text = "0.1"
    description: Text = "Utilities for interacting with Odinson via the REST API"

    authors: List[Text] = ["myedibleenso"]
    contact: Text = "ghp@lum.ai"
    repo: Text = "https://github.com/lum-ai/odinson-rest"
    license: Text = "Apache 2.0"

    @property
    def download_url(self) -> str:
        return f"{self.repo}/archive/v{self.version}.zip"


info = AppInfo()
