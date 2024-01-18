package ai.lum.odinson.rest.requests

import play.api.libs.json._

case class GrammarRequest(
  grammar: String,
  maxDocs: Option[Int] = None,
  allowTriggerOverlaps: Option[Boolean] = None,
  metadataQuery: Option[String] = None,
  pretty: Option[Boolean] = None
)

object GrammarRequest {
  implicit val fmt: OFormat[GrammarRequest] = Json.format[GrammarRequest]
  implicit val read: Reads[GrammarRequest] = Json.reads[GrammarRequest]
}
