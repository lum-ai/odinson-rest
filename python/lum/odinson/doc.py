from __future__ import annotations
import dateutil
from lum.odinson.typing import Tokens
from enum import Enum
from typing import Any, List, Literal, Optional, Sequence, Text, Tuple, Type, Union
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

    def __hash__(self) -> int:
        return hash((self.name, self.type, tuple(self.tokens)))


class GraphField(Field):
    edges: List[Tuple[int, int, Text]]
    roots: Sequence[int]
    type: Literal[Fields.GRAPH_FIELD] = pydantic.Field(
        alias="$type", default=Fields.GRAPH_FIELD.value, frozen=True
    )

    def __hash__(self) -> int:
        roots = tuple(self.roots)
        edges = tuple(
            sorted(self.edges, key=lambda triple: (triple[0], triple[1], triple[2]))
        )
        return hash((self.name, self.type, roots, edges))


class StringField(Field):
    string: Text
    type: Literal[Fields.STRING_FIELD] = pydantic.Field(
        alias="$type", default=Fields.STRING_FIELD.value, frozen=True
    )

    def __hash__(self) -> int:
        return hash((self.name, self.type, self.string))


class DateField(Field):
    date: Text
    type: Literal[Fields.DATE_FIELD] = pydantic.Field(
        alias="$type", default=Fields.DATE_FIELD.value, frozen=True
    )

    def __hash__(self) -> int:
        return hash((self.name, self.type, self.date))


class NumberField(Field):
    value: float
    type: Literal[Fields.NUMBER_FIELD] = pydantic.Field(
        alias="$type", default=Fields.NUMBER_FIELD.value, frozen=True
    )

    def __hash__(self) -> int:
        return hash((self.name, self.type, self.value))


class NestedField(Field):
    fields: List[Type[Field]]
    type: Literal[Fields.NESTED_FIELD] = pydantic.Field(
        alias="$type", default=Fields.NESTED_FIELD.value, frozen=True
    )

    def __hash__(self) -> int:
        return hash((self.name, self.type, tuple(self.fields)))


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


class Token:
    """Convenience class to represent a single token
    and its attributes"""

    def __init__(self, **kwargs):
        for k, v in kwargs.items():
            self.__dict__[k] = v

    def __len__(self):
        return len(self.raw)

    def __str__(self):
        return self.raw

    def __hash__(self):
        return hash(tuple(frozenset(sorted(self.__dict__.items()))))


class Sentence(BaseModel):
    numTokens: int
    # FIXME: figure out how to just use List[Type[Field]]
    fields: List[AnyField]
    model_config = ConfigDict(extra="allow")

    def model_post_init(self, ctx) -> None:
        """Easy access to fields"""
        tokens = [dict([]) for _ in range(self.numTokens)]
        graph = None
        for f in self.fields:
            key = f.name
            # token attributes
            if isinstance(f, TokensField):
                # populate tokens
                for i, label in enumerate(f.tokens):
                    tokens[i][key] = label
                self.__dict__[key] = f.tokens
            # syntactic dependencies, semantic roles, etc.
            elif isinstance(f, GraphField):
                graph = f
        # create tokens
        self.__dict__["tokens"] = [Token(**d) for d in tokens]

        # TODO: create graph
        # if graph is not None:
        #   edges = []
        #   self.__dict__["edges"] =
        #   key = "edges"

    def __getitem__(self, index: int) -> Token:
        return self.tokens[index]

    def __hash__(self):
        num_tokens = [self.numTokens]
        fields = [hash(f) for f in self.fields]
        components = num_tokens + fields
        return hash(tuple(components))

    def model_dump(self, by_alias=True, **kwargs):
        return super().model_dump(by_alias=by_alias, **kwargs)

    def model_dump_json(self, by_alias=True, **kwargs):
        return super().model_dump_json(by_alias=by_alias, **kwargs)

    def dict(self, **kwargs):
        return self.model_dump(**kwargs)

    def json(self, **kwargs):
        return self.model_dump_json(**kwargs)

    def copy(self, fields: List[AnyField]) -> "Sentence":
        """Convenience method for easily copying an Odinson Sentence and replacing specific attributes"""
        return Sentence(
          # validate and count tokens
          numTokens=Sentence._count_tokens(fields), 
          fields=fields
        )
    
    @staticmethod
    def validate_fields(fields: List[AnyField]) -> bool:
        # validation
        num_tokens = set()
        for f in fields:
            if isinstance(f, TokensField):
                num_tokens.add(len(f.tokens))
        # NOTE: this will also fail if no TokensField are present
        if len(num_tokens) != 1:
            raise Exception(f"All TokensField for sentence should have same length, but found {len(num_tokens)}")
        return True
        
    @staticmethod
    def _count_tokens(fields: List[AnyField]) -> bool:
      """Get count of tokens based on TokensField after first validating with Sentence.validate_fields"""
      _ = Sentence.validate_fields(fields)
      for f in fields:
          if isinstance(f, TokensField):
              return len(f.tokens)
        
    @staticmethod
    def from_fields(fields: List[AnyField]) -> "Sentence":
        """Create an Odinson Sentence from a collection of fields"""
        return Sentence(
          # validate and count
          numTokens=Sentence._count_tokens(fields), 
          fields=fields
        )
    
    @staticmethod
    def from_tokens(tokens: List[Token]) -> "Sentence":
        """Create an Odinson Sentence from a collection Tokens"""
        fields_dict = dict()
        for tok in tokens:
            for k, v in tok.__dict__.items():
                value = fields_dict.get(k, [])
                value.append(v)
                fields_dict[k] = value
        num_tokens = list({len(values) for values in fields_dict.items()}) 
        assert num_tokens == 1, "All token attributes must have the same length"
        fields = [TokensField(name=k, tokens=toks) for k, toks in fields_dict.items()]
        return Sentence(numTokens=num_tokens[0], fields=fields)
        

class Document(BaseModel):
    """ai.lum.odinson.Document"""

    id: Text
    metadata: List[AnyField]
    sentences: List[Sentence]

    def __hash__(self):
        metadata = [0] if len(self.metadata) == 0 else [hash(f) for f in self.metadata]
        sentences = [hash(s) for s in self.sentences]
        components = [hash(self.id)] + metadata + sentences
        return hash(tuple(components))

    def model_post_init(self, ctx) -> None:
        tokens = []
        attributes = dict()
        for s in self.sentences:
            # append tokens
            tokens.append(s.tokens)
            for f in s.fields:
                key = f.name
                # token attributes
                if isinstance(f, TokensField):
                    values = attributes.get(key, [])
                    # nested list
                    values.append(s.__dict__[key])
                    attributes[key] = values
        for k, v in attributes.items():
            self.__dict__[k] = v

        self.__dict__["tokens"] = [s.tokens for s in self.sentences]

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
            with gzip.open(fp, "rb") as f:
                return Document(**json.loads(f.read()))
        else:
            with open(fp, "r") as f:
                return Document(**json.loads(f.read()))

    def copy(self, id: Optional[str] = None, metadata: Optional[List[AnyField]] = None, sentences: Optional[List[Sentence]] = None) -> "Document":
        """Convenience method for easily copying an Odinson Document and replacing specific attributes"""
        return Document(
          id=id or self.id,
          metadata=metadata or self.metadata,
          sentences=sentences or self.sentences
        )