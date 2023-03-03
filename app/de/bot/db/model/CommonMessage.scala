package de.bot.db.model

import org.joda.time.DateTime
import reactivemongo.api.bson.Macros.Annotations.Key
import reactivemongo.api.bson.{BSONDateTime, BSONHandler, BSONValue, Macros}

import scala.util.{Success, Try}

sealed trait CommonMessage

case class NameMessage(text: String) extends CommonMessage
case class PhoneMessage(text: String) extends CommonMessage
case class DoctorInfoMessage(isMedic: Boolean) extends CommonMessage
case class SecurityInfoMessage(isKeeper: Boolean) extends CommonMessage
case class StarDateMessage(start: DateTime) extends CommonMessage
case class PreferableDays(text: String) extends CommonMessage
case class TimeMessage(text: String) extends CommonMessage

case class ParsedData(
  @Key("_id")
  telegramUserId: String,
  telegram: Option[String],
  name: Option[String],
  phone: Option[String],
  keeper: Option[Boolean],
  medic: Option[Boolean],
  availabilityMessage: Option[DateTime],
  days: Option[String],
  time: Option[String])

object ParsedData extends CustomDateFormatter {

  implicit val format = Macros.handler[ParsedData]
}