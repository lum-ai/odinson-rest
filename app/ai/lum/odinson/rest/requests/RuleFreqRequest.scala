package ai.lum.odinson.rest.requests

import play.api.libs.json._

case class RuleFreqRequest(
  grammar: String,
  metadataQuery: Option[String] = None,
  allowTriggerOverlaps: Option[Boolean] = None,
  // group: Option[String] = None,
  filter: Option[String] = None,
  order: Option[String] = None,
  min: Option[Int] = None,
  max: Option[Int] = None,
  scale: Option[String] = None,
  reverse: Option[Boolean] = None,
  pretty: Option[Boolean] = None
)

object RuleFreqRequest {
  implicit val fmt: OFormat[RuleFreqRequest] = Json.format[RuleFreqRequest]
  implicit val read: Reads[RuleFreqRequest] = Json.reads[RuleFreqRequest]
}
