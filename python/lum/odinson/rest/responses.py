from __future__ import annotations
from typing import Dict, Iterable, List, Optional, Text, Union
from lum.odinson.doc import Document, Sentence
from pydantic import BaseModel, ConfigDict, model_validator
from dataclasses import dataclass
import pydantic
import json
import requests
import typing


__all__ = ["CorpusInfo", "OdinsonErrors", "ScoreDoc", "Statistic", "Results"]

# OdinsonMatch = Union["NamedCapture", "GraphTraversalMatch"]

mq_desc = pydantic.Field(
    alias="metadataQuery",
    description="A query to filter Documents by their metadata before applying an Odinson pattern. See https://docs.lum.ai/odinson/metadata for details.",
    default=None,
)

duration_desc = pydantic.Field(description="The query's execution time (in seconds)")


class OdinsonErrors(BaseModel):
    errors: List[str]


class CorpusInfo(BaseModel):
    num_docs: int = pydantic.Field(alias="numDocs")
    corpus: str
    # FIXME: make this distinct graph relations
    distinct_dependency_relations: int = pydantic.Field(
        alias="distinctDependencyRelations"
    )
    token_fields: List[str] = pydantic.Field(alias="tokenFields")
    doc_fields: List[str] = pydantic.Field(alias="docFields")
    stored_fields: List[str] = pydantic.Field(alias="storedFields")
    # model_config = ConfigDict(use_enum_values=True, validate_default=True)


class Statistic(BaseModel):
    #   A term such as a token field or rule name.
    term: str
    #   A grouping term from a second token field (e.g., tag).
    group: str
    #   The number of occurrences of the term (potentially scaled).
    frequency: float


class NamedCapture(BaseModel):
    name: str
    label: str
    match: Union[BaseMatch, EventMatch, NamedCaptureMatch] = pydantic.Field(
        description="Match for capture"
        #alias="capturedMatch"
    )


class BaseMatch(BaseModel):
    start: int = pydantic.Field(
        description="Inclusive token index which denotes the start of this match's span."
    )
    end: int = pydantic.Field(
        description="Exclusive token index which denotes the end of this match's span."
    )
    text: str = pydantic.Field(description="Text corresponding to the matched span")


class EventMatch(BaseMatch):
    trigger: Union[EventMatch, NamedCaptureMatch, BaseMatch]
    named_captures: List[NamedCapture] = pydantic.Field(alias="namedCaptures")


# class NGramMatch(BaseMatch):


class NamedCaptureMatch(BaseMatch):
    named_captures: List[NamedCapture] = pydantic.Field(alias="namedCaptures")


class ScoreDoc(BaseModel):
    sentence_id: int = pydantic.Field(
        alias="sentenceId", description="The internal ID for this Odinson Document."
    )
    score: float = pydantic.Field(description="The Lucene score for this Document.")
    document_id: str = pydantic.Field(
        alias="documentId",
        description="The parent document's ID as provided at index time (uses org.clulab.processors.Document.id)",
    )
    sentence_index: int = pydantic.Field(
        alias="sentenceIndex",
        description="The index of this sentence in the parent document (0-based).",
    )
    words: List[str] = pydantic.Field(description="Tokens for the document (sentence).")
    matches: List[Union[BaseMatch, EventMatch, NamedCaptureMatch]] = pydantic.Field(
        description="The list of matching spans for this document."
    )

    def spans(self) -> Iterable[str]:
        """Convenience method for getting spans corresponding to matches"""
        for m in self.matches:
            # FIXME: should this be joined on whitespace?
            yield " ".join(self.words[m.start : m.end])


# class OdinsonSpan(BaseModel):
#     start: int = pydantic.Field(description="")
#     end: int = pydantic.Field(description="")
# class OdinsonMatch(BaseModel):
#     span: OdinsonSpan = pydantic.Field(description="")
#     captures: List[OdinsonSpan] = pydantic.Field(description="")


class BaseMention(BaseModel):
    label: str = pydantic.Field(description="The label for this Mention")
    sentence_id: int = pydantic.Field(
        alias="sentenceId", description="The internal ID for this Odinson Document."
    )
    documentId: str = pydantic.Field(
        alias="documentId",
        description="The parent document's ID as provided at index time.",
    )
    sentence_index: int = pydantic.Field(
        alias="sentenceIndex",
        description="The positional index (0-based) of the matched sentence in some Odinson Document.",
    )
    words: List[str] = pydantic.Field(description="The words of the sentence.")
    found_by: str = pydantic.Field(
        alias="foundBy", description="The name of the rule that produced this match."
    )
    match: List[Union[EventMatch, NamedCaptureMatch, BaseMatch]] = pydantic.Field(
        description="The Mention representing the match."
    )

    @model_validator(mode='before')
    @classmethod
    def validate(cls, data: typing.Any) -> typing.Any:
        if isinstance(data, dict):
            res = data.get("label", None)
            if (res is None) or (isinstance(res, str) and len(res) == 0):
              data["label"] = "???"
        return data


class GrammarResults(BaseModel):
    metadata_query: Optional[str] = mq_desc
    duration: float = duration_desc
    allow_trigger_overlaps: bool = pydantic.Field(
        alias="allowTriggerOverlaps", description=""
    )
    mentions: List[BaseMention]

    def model_dump(self, by_alias=True, **kwargs):
        return super().model_dump(by_alias=by_alias, **kwargs)

    def model_dump_json(self, by_alias=True, **kwargs):
        return super().model_dump_json(by_alias=by_alias, **kwargs)

    def dict(self, **kwargs):
        return self.model_dump(**kwargs)

    def json(self, **kwargs):
        return self.model_dump_json(**kwargs)

    # TODO: add convenience methods to get all matched spans


class Results(BaseModel):
    odinson_query: str = pydantic.Field(
        alias="odinsonQuery", description="An Odinson pattern."
    )
    metadata_query: Optional[str] = mq_desc
    duration: float = duration_desc
    total_hits: int = pydantic.Field(
        alias="totalHits",
        description="The total number of hits (matches) for the query",
    )
    score_docs: List[ScoreDoc] = pydantic.Field(
        alias="scoreDocs", description="The matches"
    )


#     # The name of the rule which matched this Mention.
#     foundBy: Text
#     matches: List[Match]
#     # match: Interval
#     #

#     #       "duration"             -> duration,
#     #       "allowTriggerOverlaps" -> allowTriggerOverlaps,
#     #       "mentions"             -> mentionsJson

#     # // format: off
#     # "sentenceId"    -> mention.luceneDocId,
#     # // "score"         -> odinsonScoreDoc.score,
#     # "label"         -> mention.label,
#     # "documentId"    -> getOdinsonDocId(mention.luceneDocId),
#     # "sentenceIndex" -> getSentenceIndex(mention.luceneDocId),
#     # "words"         -> JsArray(tokens.map(JsString)),
#     # "foundBy"       -> mention.foundBy,
#     # "match"         -> Json.arr(mkJsonForMatch(mention.odinsonMatch))


# class Interval(BaseModel):
#     # Inclusive token index which denotes the start of this match's span.
#     start: int
#     # Exclusive token index which denotes the end of this match's span.
#     end: int


# class Match(BaseModel):
#     span: Interval
#     # Named captures for this match.
#     # NamedCapture(name: String, label: Option[String], capturedMatch: OdinsonMatch)
#     captures: List[NamedCapture]

#     # def start: Int
#     # def end: Int
#     # def namedCaptures: Array[NamedCapture]

#     # /** The length of the match */
#     # def length: Int = end - start

#     # /** The interval of token indices that form this mention. */
#     # def tokenInterval: Interval = Interval.open(start, end)


# # val arguments: Map[String, Array[Mention]] = Map.empty


# class BaseMention(BaseModel):
#     odinsonMatch: OdinsonMatch
#     label: Text
#     luceneDocId: int
#     foundBy: Text
#     arguments: Dict[Text, List[BaseMention]]

#     def to_mention(self, api: OdinsonBaseAPI) -> Mention:
#         """
#         Converts to lum.odinson.Mention
#         """
#         convert = lambda role, mns: Argument(
#             role=role, mentions=[bm.to_mention(api) for bm in mns]
#         )

#         return Mention(
#             _api=api,
#             label=self.label,
#             luceneDocId=self.luceneDocId,
#             foundBy=self.foundBy,
#             arguments=[convert(role, mns) for (role, mns) in self.arguments.items()],
#         )


# @dataclass
# class Argument:
#     role: Text
#     mentions: List[Mention]


# @dataclass
# class Mention:
#     _api: OdinsonBaseAPI
#     label: Text
#     luceneDocId: int
#     foundBy: Text
#     arguments: List[Argument]
#     # todo __get__ for args

#     @property
#     def sentence(self) -> Sentence:
#         pass


# # class NamedCapture(BaseModel):
# #     name: Text
# #     label: Text
# #     match: List[NamedCapture]


# # class GraphTraversalMatch(BaseModel):
# #     srcMatch: OdinsonMatch
# #     dstMatch: OdinsonMatch
