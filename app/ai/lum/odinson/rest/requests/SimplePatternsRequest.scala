package ai.lum.odinson.rest.requests

import play.api.libs.json._

case class SimplePatternsRequest(
  patterns: List[String],
  metadataQuery: Option[String] = None,
  // label: Option[String] = None, 
  // commit: Option[Boolean] = None, 
  prevDoc: Option[Int] = None, 
  prevScore: Option[Float] = None, 
  enriched: Option[Boolean] = None,
  pretty: Option[Boolean] = None
)

object SimplePatternsRequest {
  implicit val fmt: OFormat[SimplePatternsRequest] = Json.format[SimplePatternsRequest]
  implicit val read: Reads[SimplePatternsRequest] = Json.reads[SimplePatternsRequest]
}
