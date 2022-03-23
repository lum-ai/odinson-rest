from typing import List, Text, Union
from lum.odinson.doc import Document
from lum.odinson.results import *
from lum.odinson.results import Result

# import requests


class OdinsonBaseAPI:
    def __init__(self, address: Text):
        self.address = address

    def index(self, doc: Document, max_tokens: int = -1) -> bool:
        raise NotImplementedError

    def delete(self, doc_or_id: Union[Document, Text]) -> bool:
        doc_id: Text = doc_or_id if isinstance(doc_or_id, Text) else doc_or_id.id
        raise NotImplementedError

    def update(self, doc, max_tokens: int = -1) -> bool:
        raise NotImplementedError

    def search(self, query: Text) -> List[Result]:
        raise NotImplementedError

    def validate(self, doc: Document, strict: bool = True) -> bool:
        raise NotImplementedError
