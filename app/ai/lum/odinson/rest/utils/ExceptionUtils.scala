package ai.lum.odinson.rest.utils

import org.apache.commons.lang3.exception.{ ExceptionUtils => ApacheExceptionUtils }
import play.api.libs.json._
import play.api.mvc._
import play.api.mvc.Results._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal

object ExceptionUtils {

  case class DocumentValidationError(s: String) extends Exception(s)

  /** Generate the Result for when a throwable exception is encountered */
  def describeNonFatal(
    e: Throwable,
    message: Option[String] = None
  ): Result = {
    //println(s"e:\t${ApacheExceptionUtils.getStackTrace(e)}")
    val errorMsg: String = message match {
      // .getMessage , .getStackTrace , .getRootCause
      case None      => ApacheExceptionUtils.getMessage(e)
      case Some(msg) => msg
    }
    // val json = Json.toJson(Json.obj("error" -> errorMsg))
    BadRequest(errorMsg)
  }

  /** Return a standard error handler for try blocks that throw a NullPointerException and expect a
    * Result.
    */
  def mkHandleNullPointer(message: String): PartialFunction[Throwable, Result] = {
    case _: NullPointerException => InternalServerError(message)
  }

  // def mkErrorResponse(message: String): PartialFunction[Throwable, Result] = {
  //   InternalServerError(message)
  // }

  /** Refer to the standard error handler for try blocks that throw a NonFatal exception and expect
    * a Result.
    */
  val handleNonFatal: PartialFunction[Throwable, Result] = {
    case NonFatal(e) => describeNonFatal(e)
  }

  /** Refer to the standard error handler for try blocks that throw a NonFatal exception and expect
    * a Future[Result].
    */
  def handleNonFatalInFuture(implicit
    ec: ExecutionContext
  ): PartialFunction[Throwable, Future[Result]] = {
    case NonFatal(e) => Future(describeNonFatal(e))
  }

}
