package de.bot.controllers

import com.mohiva.play.silhouette.api.exceptions.ProviderException
import com.mohiva.play.silhouette.api.util.Credentials
import com.mohiva.play.silhouette.impl.exceptions.IdentityNotFoundException
import com.mohiva.play.silhouette.impl.providers._
import de.bot.common.Calls
import de.bot.forms.{SignInForm, TotpForm}
import play.api.i18n.Messages
import play.api.mvc.{AnyContent, Request}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class SignInController @Inject()(
  scc: SilhouetteControllerComponents,
  signIn: views.html.signIn,
  activateAccount: views.html.activateAccount,
  totp: views.html.totp
)(implicit ex: ExecutionContext) extends AbstractAuthController(scc) {

  /**
   * Views the `Sign In` page.
   * @return The result to display.
   */
  def view = UnsecuredAction.async { implicit request: Request[AnyContent] =>
//    Future.successful(Ok(signIn(SignInForm.form, socialProviderRegistry)))
    Future.successful(Ok(signIn(SignInForm.form, SocialProviderRegistry(Nil))))
  }

  /**
   * Handles the submitted form.
   * @return The result to display.
   */
  def submit = UnsecuredAction.async { implicit request: Request[AnyContent] =>
    SignInForm.form.bindFromRequest.fold(
//      form => Future.successful(BadRequest(signIn(form, socialProviderRegistry))),
      form => Future.successful(BadRequest(signIn(form, SocialProviderRegistry(Nil)))),
      data => {
        val credentials = Credentials(data.email, data.password)
        credentialsProvider.authenticate(credentials).flatMap { loginInfo =>
          userService.retrieve(loginInfo).flatMap {
            case Some(user) if !user.activated =>
              Future.successful(Ok(activateAccount(data.email)))
            case Some(user)                    =>
              authInfoRepository.find[GoogleTotpInfo](user.loginInfo).flatMap {
                case Some(totpInfo) => Future.successful(Ok(totp(TotpForm.form.fill(TotpForm.Data(
                  user.userID, totpInfo.sharedKey, data.rememberMe)))))
                case _              => authenticateUser(user, data.rememberMe)
              }
            case None                          => Future.failed(new IdentityNotFoundException("Couldn't find user"))
          }
        }.recover {
          case _: ProviderException =>
            Redirect(Calls.signin).flashing("error" -> Messages("invalid.credentials"))
        }
      }
    )
  }
}
