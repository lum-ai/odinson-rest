package ai.lum.odinson.rest.requests

import play.api.libs.json._

case class SimplePatternsRequest(
  patterns: List[String],
  metadataQuery: Option[String] = None,
  label: Option[String], 
  commit: Option[Boolean], 
  prevDoc: Option[Int], 
  prevScore: Option[Float], 
  enriched: Boolean = false,
  pretty: Option[Boolean] = None
)

object SimplePatternsRequest {
  implicit val fmt: OFormat[SimplePatternsRequest] = Json.format[SimplePatternsRequest]
  implicit val read: Reads[SimplePatternsRequest] = Json.reads[SimplePatternsRequest]
}
