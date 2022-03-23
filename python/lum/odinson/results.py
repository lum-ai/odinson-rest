from __future__ import annotations
from typing import Dict, List, Text, Union
from lum.odinson.api import OdinsonBaseAPI
from lum.odinson.doc import Sentence
from pydantic import BaseModel
from dataclasses import dataclass
import pydantic

__all__ = ["Results", "Result", "Match", "Interval"]

OdinsonMatch = Union["NamedCapture", "GraphTraversalMatch"]

# @dataclass
# class OdinsonResults:
#   _API: OdinsonBaseAPI


@dataclass
class MentionsResults:
    _API: OdinsonBaseAPI
    metadataQuery: Text
    duration: float
    allowTriggerOverlaps: bool
    _mentions: List[Mention]

    # TODO: define __next__


class GrammarResult(BaseModel):
    # The internal ID for this Odinson Document.
    sentenceId: int
    # The parent document's ID as provided at index time (uses org.clulab.processors.Document.id).
    documentId: Text

    # The index of this sentence in the parent document (0-based).
    sentenceIndex: int

    # Tokens for the document (sentence).
    words: List[Text]

    # The label for this Mention.
    label: Text

    # The name of the rule which matched this Mention.
    foundBy: Text
    matches: List[Match]
    # match: Interval
    #

    #       "duration"             -> duration,
    #       "allowTriggerOverlaps" -> allowTriggerOverlaps,
    #       "mentions"             -> mentionsJson

    # // format: off
    # "sentenceId"    -> mention.luceneDocId,
    # // "score"         -> odinsonScoreDoc.score,
    # "label"         -> mention.label,
    # "documentId"    -> getOdinsonDocId(mention.luceneDocId),
    # "sentenceIndex" -> getSentenceIndex(mention.luceneDocId),
    # "words"         -> JsArray(tokens.map(JsString)),
    # "foundBy"       -> mention.foundBy,
    # "match"         -> Json.arr(mkJsonForMatch(mention.odinsonMatch))


class Interval(BaseModel):
    # Inclusive token index which denotes the start of this match's span.
    start: int
    # Exclusive token index which denotes the end of this match's span.
    end: int


class Match(BaseModel):
    span: Interval
    # Named captures for this match.
    # NamedCapture(name: String, label: Option[String], capturedMatch: OdinsonMatch)
    captures: List[NamedCapture]

    # def start: Int
    # def end: Int
    # def namedCaptures: Array[NamedCapture]

    # /** The length of the match */
    # def length: Int = end - start

    # /** The interval of token indices that form this mention. */
    # def tokenInterval: Interval = Interval.open(start, end)


# val arguments: Map[String, Array[Mention]] = Map.empty


class BaseMention(BaseModel):
    odinsonMatch: OdinsonMatch
    label: Text
    luceneDocId: int
    foundBy: Text
    arguments: Dict[Text, List[BaseMention]]

    def to_mention(self, api: OdinsonBaseAPI) -> Mention:
        """
        Converts to lum.odinson.Mention
        """
        convert = lambda role, mns: Argument(
            role=role, mentions=[bm.to_mention(api) for bm in mns]
        )

        return Mention(
            _api=api,
            label=self.label,
            luceneDocId=self.luceneDocId,
            foundBy=self.foundBy,
            arguments=[convert(role, mns) for (role, mns) in self.arguments.items()],
        )


@dataclass
class Argument:
    role: Text
    mentions: List[Mention]


@dataclass
class Mention:
    _api: OdinsonBaseAPI
    label: Text
    luceneDocId: int
    foundBy: Text
    arguments: List[Argument]
    # todo __get__ for args

    @property
    def sentence(self) -> Sentence:
        pass


class NamedCapture(BaseModel):
    name: Text
    label: Text
    match: List[NamedCapture]


class GraphTraversalMatch(BaseModel):
    srcMatch: OdinsonMatch
    dstMatch: OdinsonMatch


# ) extends OdinsonMatch {

#   val start: Int = dstMatch.start
#   val end: Int = dstMatch.end

#   def namedCaptures: Array[NamedCapture] = {
#     val srcCaps = srcMatch.namedCaptures
#     val dstCaps = dstMatch.namedCaptures
#     val length = srcCaps.length + dstCaps.length
#     val totalCaps = new Array[NamedCapture](length)
#     System.arraycopy(srcCaps, 0, totalCaps, 0, srcCaps.length)
#     System.arraycopy(dstCaps, 0, totalCaps, srcCaps.length, dstCaps.length)
#     totalCaps
#   }
# case class ArgumentMetadata(name: String, min: Int, max: Option[Int], promote: Boolean)

# class EventMatch(
#   val trigger: OdinsonMatch,
#   val namedCaptures: Array[NamedCapture],
#   val argumentMetadata: Array[


# class NGramMatch(
#   val start: Int,
#   val end: Int
# )

# class GraphTraversalMatch(
#   val srcMatch: OdinsonMatch,
#   val dstMatch: OdinsonMatch
# ) extends OdinsonMatch {

#   val start: Int = dstMatch.start
#   val end: Int = dstMatch.end

#   def namedCaptures: Array[NamedCapture] = {
#     val srcCaps = srcMatch.namedCaptures
#     val dstCaps = dstMatch.namedCaptures
#     val length = srcCaps.length + dstCaps.length
#     val totalCaps = new Array[NamedCapture](length)
#     System.arraycopy(srcCaps, 0, totalCaps, 0, srcCaps.length)
#     System.arraycopy(dstCaps, 0, totalCaps, srcCaps.length, dstCaps.length)
#     totalCaps
#   }

# }


# start*: integer
# Inclusive token index which denotes the start of this match's span.

# end*: integer
# Exclusive token index which denotes the end of this match's span.

# }]}]}]}]


class Result(BaseModel):
    # The internal ID for this Odinson Document.
    sentenceId: int
    # The Lucene score for this Document.
    score: float
    # The parent document's ID as provided at index time (uses org.clulab.processors.Document.id).
    documentId: Text
    # The index of this sentence in the parent document (0-based).
    sentenceIndex: int

    # Tokens for the document (sentence).
    words: List[Text]
    # The list of matching spans for this document.
    matches: List[Match]


class Results(BaseModel):
    # An Odinson pattern.
    odinsonQuery: Text
    # A query to filter Documents by their metadata before applying an Odinson pattern. See https://gh.lum.ai/odinson/metadata for details.
    metadataQuery: Text
    # The query's execution time (in seconds)
    duration: float
    # The total number of hits (matches) for the query
    totalHits: int
    scoreDocs: List[Result]
