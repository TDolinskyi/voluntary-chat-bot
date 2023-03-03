package de.bot.controllers

import com.mohiva.play.silhouette.api._
import com.mohiva.play.silhouette.impl.providers._
import de.bot.common.Calls
import de.bot.db.model.User
import de.bot.forms.SignUpForm
import play.api.Configuration
import play.api.i18n.Messages
import play.api.libs.mailer.Email
import play.api.mvc.{AnyContent, Request}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

/**
 * The `Sign Up` controller.
 */
class SignUpController @Inject() (
  components: SilhouetteControllerComponents,
  signUp: views.html.signUp,
  configuration: Configuration
)(implicit ex: ExecutionContext) extends SilhouetteController(components) {

  /**
   * Views the `Sign Up` page.
   *
   * @return The result to display.
   */
  def view = UnsecuredAction.async { implicit request: Request[AnyContent] =>
    Future.successful(Ok(signUp(SignUpForm.form)))
  }

  /**
   * Handles the submitted form.
   *
   * @return The result to display.
   */
  def submit = UnsecuredAction.async { implicit request: Request[AnyContent] =>
    SignUpForm.form.bindFromRequest.fold(
      form => Future.successful(BadRequest(signUp(form))),
      data => {
        val result = Redirect(de.bot.controllers.routes.SignUpController.view).flashing("info" -> Messages("sign.up.email.sent", data.email))
        val loginInfo = LoginInfo(CredentialsProvider.ID, data.email)
        userService.retrieve(loginInfo).flatMap {
          case Some(user) =>
            val url = configuration.get[String]("bot.domain") + Calls.signin.absoluteURL().replace("http://localhost:9000", "")
            mailerClient.send(Email(
              subject = Messages("email.already.signed.up.subject"),
              from = Messages("email.from"),
              to = Seq(data.email),
              bodyText = Some(views.txt.emails.alreadySignedUp(user, url).body),
              bodyHtml = Some(views.html.emails.alreadySignedUp(user, url).body)
            ))

            Future.successful(result)
          case None =>
            val authInfo = passwordHasherRegistry.current.hash(data.password)
            val user = User(
              userID = UUID.randomUUID(),
              loginInfo = loginInfo,
              firstName = Some(data.firstName),
              lastName = Some(data.lastName),
              fullName = Some(data.firstName + " " + data.lastName),
              email = Some(data.email),
              avatarURL = None,
              activated = false
            )
            for {
              avatar <- avatarService.retrieveURL(data.email)
              user <- userService.save(user.copy(avatarURL = avatar))
              authInfo <- authInfoRepository.add(loginInfo, authInfo)
              authToken <- authTokenService.create(user.userID)
            } yield {
              val url = configuration.get[String]("bot.domain") + de.bot.controllers.routes.ActivateAccountController.activate(authToken.id).absoluteURL().replace("http://localhost:9000", "")

              mailerClient.send(Email(
                subject = Messages("email.sign.up.subject"),
                from = "berlin.volunteery.bot@gmail.com",
                to = configuration.get[Seq[String]]("bot.verificationEmailReceivers"),
                bodyText = Some(views.txt.emails.signUp(user, url).body),
                bodyHtml = Some(views.html.emails.signUp(user, url).body)
              ))

              eventBus.publish(SignUpEvent(user, request))
              result
            }
        }
      }
    )
  }
}
