package de.bot.db.model

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.libs.json.{JsString, JsValue, Reads, Writes}
import reactivemongo.api.bson.{BSONBinary, BSONDateTime, BSONHandler, BSONReader, BSONValue, BSONWriter, Subtype}

import java.io.{ByteArrayOutputStream, DataOutputStream}
import java.util.UUID
import scala.util.{Success, Try}

trait CustomDateFormatter {

  val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

  implicit val jodaDateReads = Reads[DateTime](js =>
    js.validate[String].map[DateTime](dtString =>
      DateTime.parse(dtString, DateTimeFormat.forPattern(dateFormat))
    )
  )

  implicit val jodaDateWrites: Writes[DateTime] = new Writes[DateTime] {
    def writes(d: DateTime): JsValue = JsString(d.toString())
  }

  implicit object BSONDateTimeHandler extends BSONHandler[DateTime] {
    def readTry(bson: BSONValue): Try[DateTime] = bson.asTry[BSONDateTime].map(v => new DateTime(v.value))
    def writeTry(date: DateTime): Try[BSONDateTime] = Success(BSONDateTime(date.getMillis))
  }

}
