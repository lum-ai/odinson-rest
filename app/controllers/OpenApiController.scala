package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class OpenApiController @Inject() (cc: ControllerComponents)
    extends AbstractController(cc) {

  def openAPI() = Action {
    Ok(views.html.api())
  }

}
