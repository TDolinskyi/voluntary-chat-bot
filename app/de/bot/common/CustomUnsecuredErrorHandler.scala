package de.bot.common

import com.mohiva.play.silhouette.api.actions.UnsecuredErrorHandler
import play.api.mvc.RequestHeader
import play.api.mvc.Results._

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

class CustomUnsecuredErrorHandler @Inject()() extends UnsecuredErrorHandler {

  /**
   * Called when a user is authenticated but not authorized.
   *
   * As defined by RFC 2616, the status code of the response should be 403 Forbidden.
   * @param request The request header.
   * @return The result to send to the client.
   */
  override def onNotAuthorized(implicit request: RequestHeader) = {
    Future.successful(Redirect(de.bot.controllers.routes.MessageController.index))
  }
}