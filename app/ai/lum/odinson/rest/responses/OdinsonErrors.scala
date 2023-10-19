package ai.lum.odinson.rest.responses

import play.api.libs.json._

case class OdinsonErrors(
  errors: Seq[String]
)

object OdinsonErrors {
  implicit val fmt: OFormat[OdinsonErrors] = Json.format[OdinsonErrors]
  implicit val read: Reads[OdinsonErrors] = Json.reads[OdinsonErrors]

  def fromException(error: Throwable): OdinsonErrors = {
    // FIXME: parse this
    val errors = Seq(error.getMessage)
    OdinsonErrors(errors = errors)
  }

}
