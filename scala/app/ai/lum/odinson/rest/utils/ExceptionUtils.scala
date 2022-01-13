package ai.lum.odinson.rest.utils

import org.apache.commons.lang3.exception.{ ExceptionUtils => ApacheExceptionUtils }
import play.api.libs.json._
import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

object ExceptionUtils {
  /** Generate the Result for when a throwable exception is encountered */
  def describeNonFatal(e: Throwable): Result = {
    // .getMessage , .getStackTrace , .getRootCause
    val errorMsg = ApacheExceptionUtils.getMessage(e)
    val json = Json.toJson(Json.obj("error" -> errorMsg))
    BadRequest(json)
  }

  /** Return a standard error handler for try blocks that throw a NullPointerException and expect a Result. */
  def mkHandleNullPointer(message: String): PartialFunction[Throwable, Result] = {
    case _: NullPointerException => InternalServerError(message)
  }

  /** Refer to the standard error handler for try blocks that throw a NonFatal exception and expect a Result. */
  val handleNonFatal: PartialFunction[Throwable, Result] = {
    case NonFatal(e) => describeNonFatal(e)
  }

  /** Refer to the standard error handler for try blocks that throw a NonFatal exception and expect a Future[Result]. */
  def handleNonFatalInFuture(implicit ec: ExecutionContext): PartialFunction[Throwable, Future[Result]] = {
    case NonFatal(e) => Future(describeNonFatal(e))
  }
}
