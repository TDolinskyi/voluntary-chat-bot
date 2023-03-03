package de.bot.db.model

import java.util.UUID
import play.api.libs.json._
import com.mohiva.play.silhouette.api.{Identity, LoginInfo}
import reactivemongo.api.bson.Macros

case class User(
  userID: UUID,
  loginInfo: LoginInfo,
  firstName: Option[String],
  lastName: Option[String],
  fullName: Option[String],
  email: Option[String],
  avatarURL: Option[String],
  activated: Boolean
) extends Identity {

  def name = fullName.orElse {
    firstName -> lastName match {
      case (Some(f), Some(l)) => Some(f + " " + l)
      case (Some(f), None) => Some(f)
      case (None, Some(l)) => Some(l)
      case _ => None
    }
  }
}

object User extends CustomDateFormatter {
  import LoginInfo._
  implicit val jsonFormat = Json.format[User]
}
