package ai.lum.odinson.rest.requests

import play.api.libs.json._

case class RuleHistRequest(
  grammar: String,
  allowTriggerOverlaps: Option[Boolean] = None,
  bins: Option[Int],
  equalProbability: Option[Boolean],
  xLogScale: Option[Boolean],
  pretty: Option[Boolean]
)

object RuleHistRequest {
  implicit val fmt: OFormat[RuleHistRequest] = Json.format[RuleHistRequest]
  implicit val read: Reads[RuleHistRequest] = Json.reads[RuleHistRequest]
}
