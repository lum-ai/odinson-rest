from __future__ import annotations
import dateutil
from lum.odinson.typing import Tokens
from enum import Enum
from typing import List, Literal, Sequence, Text, Tuple, Type, Union
import abc
from pydantic import BaseModel, ConfigDict
import pydantic
import gzip
import json

__all__ = ["Document", "AnyField"]


class Fields(Text, Enum):
    # $type      ai.lum.odinson.*
    TOKENS_FIELD = "ai.lum.odinson.TokensField"
    GRAPH_FIELD = "ai.lum.odinson.GraphField"
    STRING_FIELD = "ai.lum.odinson.StringField"
    DATE_FIELD = "ai.lum.odinson.DateField"
    NUMBER_FIELD = "ai.lum.odinson.NumberField"
    NESTED_FIELD = "ai.lum.odinson.NestedField"

    def __repr__(self) -> str:
        return str.__repr__(self.value)

class Field(BaseModel):
    name: Text
    type: Fields = pydantic.Field(alias="$type", default="ai.lum.odinson.Field")
    model_config = ConfigDict(use_enum_values=True, validate_default=True)


class TokensField(Field):
    tokens: Tokens
    type: Literal[Fields.TOKENS_FIELD] = pydantic.Field(
        alias="$type", default=Fields.TOKENS_FIELD.value, frozen=True
    )


class GraphField(Field):
    edges: List[Tuple[int, int, Text]]
    roots: Sequence[int]
    type: Literal[Fields.GRAPH_FIELD] = pydantic.Field(
        alias="$type", default=Fields.GRAPH_FIELD.value, frozen=True
    )


class StringField(Field):
    string: Text
    type: Literal[Fields.STRING_FIELD] = pydantic.Field(
        alias="$type", default=Fields.STRING_FIELD.value, frozen=True
    )


class DateField(Field):
    date: Text
    type: Literal[Fields.DATE_FIELD] = pydantic.Field(
        alias="$type", default=Fields.DATE_FIELD.value, frozen=True
    )


class NumberField(Field):
    value: float
    type: Literal[Fields.NUMBER_FIELD] = pydantic.Field(
        alias="$type", default=Fields.NUMBER_FIELD.value, frozen=True
    )


class NestedField(Field):

    fields: List[Type[Field]]
    type: Literal[Fields.NESTED_FIELD] = pydantic.Field(
        alias="$type", default=Fields.NESTED_FIELD.value, frozen=True
    )


AnyField = Union[
    TokensField, GraphField, StringField, DateField, NumberField, NestedField
]

class Metadata:
    """Utility methods for contructing metadata"""
    @staticmethod
    def from_dict(d) -> List[AnyField]:
        fields = []
        for fname, v in d.items():
            if isinstance(v, str):
                # try parsing as date
                try:
                    _ = dateutil.parser.parse(v)
                    fields.append(DateField(name=fname, date=v))
                except:
                    fields.append(StringField(name=fname, string=v))
            elif isinstance(v, float) or isinstance(v, int):
                fields.append(NumberField(name=fname, value=float(v)))
            elif isinstance(v, list):
                # if all elems are str -> TokensField
                pass
            elif isinstance(v, dict):
                # if contains "edges" and "roots" -> GraphField
                pass


class Sentence(BaseModel):
    numTokens: int
    # FIXME: figure out how to just use List[Type[Field]]
    fields: List[AnyField]
    def model_dump(self, by_alias=True, **kwargs):
      return super().model_dump(by_alias=by_alias, **kwargs)
    def model_dump_json(self, by_alias=True, **kwargs):
      return super().model_dump_json(by_alias=by_alias, **kwargs)
    def dict(self, **kwargs):
       return self.model_dump(**kwargs)
    def json(self, **kwargs):
       return self.model_dump_json(**kwargs)

class Document(BaseModel):
    """ai.lum.odinson.Document"""

    id: Text
    metadata: List[AnyField]
    sentences: List[Sentence]

    def metadata_by_name(self, name: str) -> List[AnyField]:
       return [m for m in self.metadata if m.name == name]

    def metadata_by_type(self, mtype: AnyField) -> List[AnyField]:
       return [m for m in self.metadata if m.type == mtype]

    def model_dump(self, by_alias=True, **kwargs):
      return super().model_dump(by_alias=by_alias, **kwargs)
    def model_dump_json(self, by_alias=True, **kwargs):
      return super().model_dump_json(by_alias=by_alias, **kwargs)
    
    def dict(self, **kwargs):
       return self.model_dump(**kwargs)
    def json(self, **kwargs):
       return self.model_dump_json(**kwargs)
    
    @staticmethod
    def from_file(fp: Text) -> Document:
      if fp.lower().endswith(".gz"):
        with gzip.open(fp, 'rb') as f:
          return Document(**json.loads(f.read()))
      else:
        with open(fp, 'r') as f:
          return Document(**json.loads(f.read()))