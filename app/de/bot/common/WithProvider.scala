package de.bot.common


import com.mohiva.play.silhouette.api.{Authenticator, Authorization}
import de.bot.db.model.User
import play.api.mvc.Request

import scala.concurrent.Future

case class WithProvider[A <: Authenticator](provider: String) extends Authorization[User, A] {

  override def isAuthorized[B](user: User, authenticator: A)(
    implicit
    request: Request[B]
  ): Future[Boolean] = {

    Future.successful(user.loginInfo.providerID == provider)
  }
}
