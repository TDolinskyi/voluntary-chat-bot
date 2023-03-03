package de.bot.controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import com.mohiva.play.silhouette.api.{LogoutEvent, Silhouette}
import de.bot.common.{Calls, DefaultEnv}
import de.bot.services.{BotMessagesService, MessageService}
import play.api.mvc.AnyContent
import com.mohiva.play.silhouette.api.actions._
import play.api.mvc._

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

class MessageController @Inject()(
  scc: SilhouetteControllerComponents,
  botMessagesService: BotMessagesService,
  messagesService: MessageService,
  botInfo: views.html.botInfo
)(implicit ex: ExecutionContext, system: ActorSystem, mat: Materializer) extends AbstractAuthController(scc) {

  def index = SecuredAction.async { implicit request: SecuredRequest[EnvType, AnyContent] =>
    botMessagesService.getMe.map {
      _.map {
        res => Ok(botInfo(res.result.username.getOrElse("Unspecified"), request.identity))
      }.getOrElse {
        Ok(botInfo("Unspecified", request.identity))
      }
    }
  }

  //  def runHook = Action.async {
  //    botMessagesService.runBot.map(_ => Ok("Bot is running"))
  //  }
  //
  //  def stopHook = Action {
  //    botMessagesService.stopBot
  //    Ok("Bot is stopped")
  //  }
  //
  //  def hook = Action {
  //    implicit response =>
  //      println(response.body.toString)
  //
  //      Ok(response.body.toString)
  //  }

  def signOut = SecuredAction.async { implicit request: SecuredRequest[EnvType, AnyContent] =>
    val result = Redirect(Calls.signin)
    eventBus.publish(LogoutEvent(request.identity, request))
    authenticatorService.discard(request.authenticator, result)
  }

  def getAll = SecuredAction.async {
    implicit res =>
      messagesService.getAll().map(Ok(_))
  }

}
