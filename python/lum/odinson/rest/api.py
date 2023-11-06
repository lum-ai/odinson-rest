from __future__ import annotations
from typing import Any, Dict, Iterator, List, Literal, Optional, Text, Union
from lum.odinson.doc import AnyField, Document, Sentence
from lum.odinson.rest.responses import CorpusInfo, OdinsonErrors, ScoreDoc, Statistic, Results
from pydantic import BaseModel
from dataclasses import dataclass
import pydantic
import json
import requests
import urllib.parse

__all__ = ["OdinsonBaseAPI"]

# __all__ = ["Results", "Result", "Match", "Interval"]


class OdinsonBaseAPI:

    def __init__(self, address: Text):
        self.address = address

    @staticmethod
    def status_code_to_bool(code: int) -> bool:
        return True if code == requests.codes.ok else False

    def __len__(self) -> int:
        return self.numdocs

    @property
    def numdocs(self) -> int:
        """Total number of documents (num. docs = num. sentences) in the corpus."""
        endpoint = f"{self.address}/api/numdocs"
        return requests.get(endpoint).json()

    @property
    def tags_vocabulary(self) -> List[str]:
        """Retrieves vocabulary of part-of-speech tags for the current index."""
        endpoint = f"{self.address}/api/tags-vocabulary"
        return requests.get(endpoint).json()

    @property
    def edge_vocabulary(self) -> List[str]:
        """Retrieves vocabulary of dependencies for the current index."""
        # FIXME: change this to edge-vocabulary
        endpoint = f"{self.address}/api/dependencies-vocabulary"
        return requests.get(endpoint).json()

    def corpus(self) -> CorpusInfo:
        """Provides a summary of the current index"""
        endpoint = f"{self.address}/api/corpus"
        # return requests.get(endpoint).json()
        return CorpusInfo(**requests.get(endpoint).json())

    # api/config
    def buildinfo(self) -> Dict[str, Union[str, List[str], bool]]:
        """Provides detailed build information about the currently running app."""
        endpoint = f"{self.address}/api/buildinfo"
        return requests.get(endpoint).json()

    # api/config
    def _config(self) -> Dict[str, Any]:
        """Provides detailed build information about the currently running app."""
        endpoint = f"{self.address}/api/config"
        return requests.get(endpoint).json()

    def term_freq(self) -> List[Statistic]:
        pass

    def rule_freq(
        self,
        # An Odinson grammar.
        grammar: str,
        # Whether or not event arguments are permitted to overlap with the event's trigger. Defaults to false.
        allow_trigger_overlaps: bool = False,
        # The order in which to return results: "freq" (frequency order, default) or "alpha" (alphanumeric order).
        order: Literal["freq", "alpha"] = "freq",
        # The smallest rank to return, with 0 (default) being the highest ranked.
        min: int = 0,
        # The highest rank to return, e.g. 9 (default).
        max: int = 0,
        # Scaling to apply to frequency counts. Choices are "count" (default), "log10", and "percent".
        scale: Literal["count", "log10", "percent"] = "count",
        # Whether to reverse the rank order, to select the 10 lease frequent results, for example.
        reverse: bool = False,
    ) -> List[Statistic]:
        payload = {
            "grammar": grammar,
            "allowTriggerOverlaps": allow_trigger_overlaps,
            "order": order,
            "min": min,
            "max": max,
            "scale": scale,
            "reverse": reverse,
            "pretty": False,
        }
        endpoint = f"{self.address}/api/rule-freq"
        return requests.post(endpoint, json=payload).json()

    def _post_doc(
        self, endpoint: str, doc: Document, headers: Optional[Dict[str, str]] = None
    ) -> requests.Response:
        return requests.post(
            endpoint,
            json=doc.dict(),
            # NOTE: data takes str & .json() returns json str
            # strange as it seems, this round trip is seems necessary for at least some files
            # data=json.dumps(json.loads(doc.json())),
            headers=headers,
        )

    def _post_text(
        self, endpoint: str, text: str, headers: Optional[Dict[str, str]] = None
    ) -> requests.Response:
        return requests.post(
            endpoint,
            # NOTE: data takes str & .json() returns json str
            #json=text,
            data=text,
            headers=headers
        )

    def validate_document(self, doc: Document, strict: bool = True) -> bool:
        """Inspects and validates an OdinsonDocument"""
        endpoint = (
            f"{self.address}/api/validate/document/strict"
            if strict
            else f"{self.address}/api/validate/document/relaxed"
        )
        res = self._post_doc(endpoint=endpoint, doc=doc)
        return OdinsonBaseAPI.status_code_to_bool(res.status_code)

    def validate_rule(
        self, rule: str, verbose: bool = False
    ) -> Union[bool, OdinsonErrors]:
        """Inspects and validates an Odinson rule"""
        endpoint = f"{self.address}/api/validate/rule"
        res = self._post_text(endpoint=endpoint, text=rule)
        if res.status_code == 200:
            return OdinsonBaseAPI.status_code_to_bool(res.status_code)
        else:
            return False if not verbose else OdinsonErrors.model_validate(res.json())

    def validate_grammar(
        self, grammar: str, verbose: bool = False
    ) -> Union[bool, OdinsonErrors]:
        """Inspects and validates an Odinson grammar"""
        endpoint = f"{self.address}/api/validate/grammar"
        res = self._post_text(endpoint=endpoint, contents=grammar)
        if res.status_code == 200:
            return OdinsonBaseAPI.status_code_to_bool(res.status_code)
        else:
            return False if not verbose else OdinsonErrors.model_validate(res.json())

    def index(self, doc: Document, max_tokens: int = -1) -> bool:
        """Indexes a single Document"""
        # endpoint = f"{self.address}/api/index/document"
        endpoint = (
            f"{self.address}/api/index/document/maxTokensPerSentence/{max_tokens}"
        )
        # NOTE: data takes str & .json() returns json str
        headers = {"Content-type": "application/json", "Accept": "text/plain"}
        res = self._post_doc(endpoint=endpoint, doc=doc, headers=headers)
        return OdinsonBaseAPI.status_code_to_bool(res.status_code)

    def update(self, doc: Document, max_tokens: Optional[int] = None) -> bool:
        """Updates an OdinsonDocument in the index, allowing for a specified maximum number of tokens per sentence."""
        endpoint = (
            f"{self.address}/api/update/document/{urllib.parse.quote(doc.id)}"
            if not max_tokens
            else f"{self.address}/api/update/document/maxTokensPerSentence/{max_tokens}"
        )
        res = self._post_doc(endpoint=endpoint, doc=doc)
        return OdinsonBaseAPI.status_code_to_bool(res.status_code)

    def delete(self, doc_or_id: Union[Document, Text]) -> bool:
        """Removes an OdinsonDocument from the index."""
        doc_id: Text = doc_or_id if isinstance(doc_or_id, Text) else doc_or_id.id
        endpoint = f"{self.address}/api/delete/document/{doc_id}"
        res = requests.delete(endpoint)
        return OdinsonBaseAPI.status_code_to_bool(res.status_code)

    def sentence(self, sentence_id: int) -> Sentence:
        """Retrieves an Odinson Sentence from the doc store."""
        endpoint = f"{self.address}/api/sentence/{sentence_id}"
        res = requests.get(endpoint)
        return Sentence.model_validate(res.json())

    def document(self, document_id: str) -> Document:
        """Retrieves an Odinson Document from the doc store."""
        endpoint = f"{self.address}/api/document/{document_id}"
        res = requests.get(endpoint)
        return Document.model_validate(res.json())

    def metadata_for_sentence(self, sentence_id: str) -> List[AnyField]:
        """Retrieves Odinson Document Metadata from the doc store."""
        endpoint = f"{self.address}/api/metadata/sentence/{sentence_id}"
        res = requests.get(endpoint)
        doc = Document.model_validate(
            {"id": "UNK", "metadata": res.json(), "sentences": []}
        )
        return doc.metadata

    def metadata_for_document(self, document_id: str) -> List[AnyField]:
        """Retrieves Odinson Document Metadata from the doc store."""
        endpoint = f"{self.address}/api/metadata/document/{document_id}"
        res = requests.get(endpoint)
        # print(res.json())
        doc = Document.model_validate(
            {"id": document_id, "metadata": res.json(), "sentences": []}
        )
        return doc.metadata

    def metadata(self, id: Union[str, int]) -> List[AnyField]:
        """Retrieves Odinson Document Metadata from the doc store."""
        if isinstance(id, str):
            return self.metadata_for_document(id)
        elif isinstance(id, int):
            return self.metadata_for_sentence(id)

    # /api/parent/sentence/:sentenceId
    # /api/metadata/document/:odinsonDocId
    # /api/metadata/sentence/:sentenceId

    def _search(
        self,
        # An Odinson pattern.
        # Example: [lemma=pie] []
        odinson_query: str,
        # A query to filter Documents by their metadata before applying an Odinson pattern.
        metadata_query: Optional[str] = None,
        # The label to use when committing mentions to the State.
        # Example: character contains 'Special Agent'
        label: Optional[str] = None,
        # Whether or not the results of this query should be committed to the State.
        commit: bool = False,
        # The ID (sentenceId) for the last document (sentence) seen in the previous page of results.
        prev_doc: Optional[int] = None,
        # The score for the last result seen in the previous page of results.
        prev_score: Optional[float] = None,
    ) -> Results:  # -> Iterator[S]:
        endpoint = f"{self.address}/api/execute/pattern"
        params = {
            "odinsonQuery": odinson_query,
            "metadataQuery": metadata_query,
            "label": label,
            "commit": commit,
            "prevDoc": prev_doc,
            "prevScore": prev_score,
        }
        params = {k: v for (k, v) in params.items() if v}
        # print(params)
        res = requests.get(endpoint, params=params)
        # print(res)
        return Results.empty() if res.status_code != 200 else Results(**res.json())

    def search(
        self,
        # An Odinson pattern.
        # Example: [lemma=pie] []
        odinson_query: str,
        # A query to filter Documents by their metadata before applying an Odinson pattern.
        metadata_query: Optional[str] = None,
        # The label to use when committing mentions to the State.
        # Example: character contains 'Special Agent'
        label: Optional[str] = None,
        # Whether or not the results of this query should be committed to the State.
        commit: bool = False,
        # The ID (sentenceId) for the last document (sentence) seen in the previous page of results.
        prev_doc: Optional[int] = None,
        # The score for the last result seen in the previous page of results.
        prev_score: Optional[float] = None,
    ) -> Iterator[ScoreDoc]:
        endpoint = f"{self.address}/api/execute/pattern"
        params = {
            "odinsonQuery": odinson_query,
            "metadataQuery": metadata_query,
            "label": label,
            "commit": commit,
            "prevDoc": prev_doc,
            "prevScore": prev_score,
        }
        seen = 0
        results: Results = self._search(
            odinson_query=odinson_query,
            metadata_query=metadata_query,
            label=label,
            commit=commit,
            prev_doc=prev_doc,
        )
        total = results.total_hits
        if total == 0:
            return iter(())
        last = results.score_docs[-1]
        while seen < total:
            for sd in results.score_docs:
                seen += 1
                last = sd
                # print(f"{seen-1}/{total}")
                # print(f"sd.document_id:\t{sd.document_id}")
                # print(f"sd.sentence_id:\t{sd.sentence_id}\n")
                # FIXME: should this be a Results() with a single doc?
                yield sd
            # paginate
            results: Results = self._search(
                odinson_query=odinson_query,
                metadata_query=metadata_query,
                label=label,
                commit=commit,
                prev_doc=last.sentence_id,
            )
            # print(f"total_hits:\t{results.total_hits}")

    # TODO: add method to retrieve doc for id
    # TODO: add rewrite method
    # for any token that matches the pattern, replace its entry in field <field> with <label>
    # ex [word="Table" & tag=/NNP.*/] -> {scratch: "CAPTION"}
