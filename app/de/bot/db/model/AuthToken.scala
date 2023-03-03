package de.bot.db.model

import java.util.UUID
import play.api.libs.json._
import org.joda.time.DateTime
import reactivemongo.api.bson.Macros

case class AuthToken(
  id: UUID,
  userID: UUID,
  expiry: DateTime
)

object AuthToken extends CustomDateFormatter {
  implicit val jsonFormat = Json.format[AuthToken]
}
