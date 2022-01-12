package ai.lum.odinson.rest.requests

import play.api.libs.json._

case class GrammarRequest(
  grammar: String,
  pageSize: Option[Int] = None,
  allowTriggerOverlaps: Option[Boolean] = None,
  pretty: Option[Boolean] = None
)

object GrammarRequest {
  implicit val fmt: OFormat[GrammarRequest] = Json.format[GrammarRequest]
  implicit val read: Reads[GrammarRequest] = Json.reads[GrammarRequest]
}