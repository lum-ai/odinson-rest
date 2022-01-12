from __future__ import annotations
from lum.odinson.typing import Tokens
from enum import Enum
from typing import List, Literal, Set, Text, Tuple, Type, Union
import abc
from pydantic import BaseModel
import pydantic

__all__ = ["Document", "AnyField"]


class Fields(Text, Enum):
    # $type      ai.lum.odinson.*
    TOKENS_FIELD = "ai.lum.odinson.TokensField"
    GRAPH_FIELD = "ai.lum.odinson.GraphField"
    STRING_FIELD = "ai.lum.odinson.StringField"
    DATE_FIELD = "ai.lum.odinson.DateField"
    NUMBER_FIELD = "ai.lum.odinson.NumberField"
    NESTED_FIELD = "ai.lum.odinson.NestedField"


class Field(BaseModel):
    name: Text
    type: Fields = pydantic.Field(alias="$type", default="ai.lum.odinson.Field")

    class Config:
        use_enum_values = True


class TokensField(Field):
    tokens: Tokens
    type: Literal[Fields.TOKENS_FIELD] = pydantic.Field(
        alias="$type", default=Fields.TOKENS_FIELD
    )


class GraphField(Field):
    edges: List[Tuple[int, int, Text]]
    roots: Set[int]
    type: Literal[Fields.GRAPH_FIELD] = pydantic.Field(
        alias="$type", default=Fields.GRAPH_FIELD
    )


class StringField(Field):
    string: Text
    type: Literal[Fields.STRING_FIELD] = pydantic.Field(
        alias="$type", default=Fields.STRING_FIELD
    )


class DateField(Field):
    date: Text
    type: Literal[Fields.DATE_FIELD] = pydantic.Field(
        alias="$type", default=Fields.DATE_FIELD
    )


class NumberField(Field):
    value: float
    type: Literal[Fields.NUMBER_FIELD] = pydantic.Field(
        alias="$type", default=Fields.NUMBER_FIELD
    )


class NestedField(Field):

    fields: List[Type[Field]]
    type: Literal[Fields.NESTED_FIELD] = pydantic.Field(
        alias="$type", default=Fields.NESTED_FIELD
    )


AnyField = Union[
    TokensField, GraphField, StringField, DateField, NumberField, NestedField
]


class Sentence(BaseModel):
    numTokens: int
    # FIXME: figure out how to just use List[Type[Field]]
    fields: List[AnyField]


class Document(BaseModel):
    """ai.lum.odinson.Document"""

    id: Text
    # FIXME: figure out how to just use List[Type[Field]]
    # metadata: List[AnyField] = []
    metadata: List[AnyField]
    sentences: List[Sentence]
