from __future__ import annotations
from typing import Optional
from pydantic import BaseModel, ConfigDict


__all__ = ["GrammarRequest"]


class GrammarRequest(BaseModel):
    grammar: str
    metadataQuery: Optional[str] = None
    maxDocs: Optional[int] = None
    allowTriggerOverlaps: Optional[bool] = None
    pretty: Optional[bool] = None

    def model_dump(self, by_alias=True, **kwargs):
        return super().model_dump(by_alias=by_alias, **kwargs)

    def model_dump_json(self, by_alias=True, **kwargs):
        return super().model_dump_json(by_alias=by_alias, **kwargs)

    def dict(self, **kwargs):
        return self.model_dump(**kwargs)

    def json(self, **kwargs):
        return self.model_dump_json(**kwargs)