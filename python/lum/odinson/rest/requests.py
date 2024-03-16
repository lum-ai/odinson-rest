from __future__ import annotations
import typing
from pydantic import BaseModel, ConfigDict


__all__ = ["GrammarRequest", "SimplePatternsRequest"]


class GrammarRequest(BaseModel):
    grammar: str
    metadataQuery: typing.Optional[str] = None
    maxDocs: typing.Optional[int] = None
    allowTriggerOverlaps: typing.Optional[bool] = None
    pretty: typing.Optional[bool] = None

    def model_dump(self, by_alias=True, **kwargs):
        return super().model_dump(by_alias=by_alias, **kwargs)

    def model_dump_json(self, by_alias=True, **kwargs):
        return super().model_dump_json(by_alias=by_alias, **kwargs)

    def dict(self, **kwargs):
        return self.model_dump(**kwargs)

    def json(self, **kwargs):
        return self.model_dump_json(**kwargs)


class SimplePatternsRequest(BaseModel):
    patterns: list[str]
    metadataQuery: typing.Optional[str] = None
    prevDoc: typing.Optional[int] = None
    prevScore: typing.Optional[float] = None
    pretty: typing.Optional[bool] = None

    def model_dump(self, by_alias=True, **kwargs):
        return super().model_dump(by_alias=by_alias, **kwargs)

    def model_dump_json(self, by_alias=True, **kwargs):
        return super().model_dump_json(by_alias=by_alias, **kwargs)

    def dict(self, **kwargs):
        return self.model_dump(**kwargs)

    def json(self, **kwargs):
        return self.model_dump_json(**kwargs)
