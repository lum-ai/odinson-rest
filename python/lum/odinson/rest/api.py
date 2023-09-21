from __future__ import annotations
from typing import Any, Dict, Iterator, List, Literal, Optional, Text, Union
from lum.odinson.doc import (Document, Sentence)
from lum.odinson.rest.responses import (CorpusInfo, Statistic, Results)
from pydantic import BaseModel
from dataclasses import dataclass
import pydantic
import json
import requests
import urllib.parse

__all__ = ["OdinsonBaseAPI"]

#__all__ = ["Results", "Result", "Match", "Interval"]


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
        """"""
        endpoint = f"{self.address}/api/corpus"
        #return requests.get(endpoint).json()
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
    
    def term_freq(
      self
    ) -> List[Statistic]:
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
      reverse: bool = False
    ) -> List[Statistic]:
      payload = {
        "grammar": grammar,
        "allowTriggerOverlaps": allow_trigger_overlaps,
        "order": order,
        "min": min,
        "max": max,
        "scale": scale,
        "reverse": reverse,
        "pretty": False
      }
      endpoint = f"{self.address}/api/rule-freq"
      return requests.post(endpoint, json=payload).json()


    def _post_doc(
      self, 
      endpoint: str, 
      doc: Document, 
      headers: Optional[Dict[str, str]] = None
    ) -> requests.Response:
        return requests.post(
            endpoint, 
            json=doc.dict(),
            # NOTE: data takes str & .json() returns json str
            # strange as it seems, this round trip is seems necessary for at least some files
            #data=json.dumps(json.loads(doc.json())),
            headers=headers
        )

    def validate(self, doc: Document, strict: bool = True) -> bool:
        """Inspects and validates an OdinsonDocument"""
        endpoint = f"{self.address}/api/validate/strict" if strict else f"{self.address}/api/validate/relaxed"
        res = self._post_doc(endpoint=endpoint, doc=doc)
        return OdinsonBaseAPI.status_code_to_bool(res.status_code)
    
    def index(self, doc: Document, max_tokens: int = -1) -> bool:
        #endpoint = f"{self.address}/api/index/document"
        endpoint = f"{self.address}/api/index/document/maxTokensPerSentence/{max_tokens}"
        # NOTE: data takes str & .json() returns json str
        headers = {'Content-type': 'application/json', 'Accept': 'text/plain'}
        res = self._post_doc(endpoint=endpoint, doc=doc, headers=headers)
        return OdinsonBaseAPI.status_code_to_bool(res.status_code)

    def update(self, doc: Document, max_tokens: Optional[int] = None) -> bool:
        """Updates an OdinsonDocument in the index, allowing for a specified maximum number of tokens per sentence."""
        endpoint = f"{self.address}/api/update/document/{urllib.parse.quote(doc.id)}" if not max_tokens else f"{self.address}/api/update/document/maxTokensPerSentence/{max_tokens}"
        res = self._post_doc(endpoint=endpoint, doc=doc)
        return OdinsonBaseAPI.status_code_to_bool(res.status_code)
    
    def delete(self, doc_or_id: Union[Document, Text]) -> bool:
        """Removes an OdinsonDocument from the index."""
        doc_id: Text = doc_or_id if isinstance(doc_or_id, Text) else doc_or_id.id
        endpoint = f"{self.address}/api/delete/document/{doc_id}"
        res = requests.delete(endpoint)
        return OdinsonBaseAPI.status_code_to_bool(res.status_code)

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
        prev_score: Optional[float] = None 
    ) -> Results: #-> Iterator[S]:
        endpoint = f"{self.address}/api/execute/pattern"
        params = {
          "odinsonQuery": odinson_query,
          "metadataQuery": metadata_query,
          "label": label,
          "commit": commit,
          "prevDoc": prev_doc,
          "prevScore": prev_score
        }
        params = {k:v for (k, v) in params.items() if v}
        print(params)
        res = requests.get(endpoint, params=params).json()
        print(res)
        return Results(**res)
        # get res
        # remaining = True
        # while remaining:
        #  
        #return res
        # for sd in res:
        #     # TODO: return a generator where we get next res until none left.
        #     print(_res)
        #     r = Result(**_res)
        #     yield r

