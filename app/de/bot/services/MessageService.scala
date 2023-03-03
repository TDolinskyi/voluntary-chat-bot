package de.bot.services

import de.bot.db.dao.MessageDao
import de.bot.db.model.ParsedData
import de.bot.services.MessageService.fromParsedDataToGrouped
import org.joda.time.LocalDate
import play.api.libs.json.{JsBoolean, Json}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class MessageService @Inject()(messageDao: MessageDao)(implicit ec: ExecutionContext) {

  def getAll() = {
    messageDao.getAll.map(fromParsedDataToGrouped)
  }

}

object MessageService {

  val UNSPECIFIED = "unspecified"

  case class VolunteerInfo(name: String, phone: String, time: String, telegram: String)

  case class MedicalSortedList(medical: List[VolunteerInfo], other: List[VolunteerInfo])

  case class DateGroupedResponse(date: LocalDate, medicalSortedList: MedicalSortedList)

  case class APIResponse(data: List[DateGroupedResponse])

  def fromParsedDataToGrouped(data: List[ParsedData]) = {

      Json.toJson(data.sortBy(_.availabilityMessage.map(_.getMillis))
        .map {
          case value => Json.toJson(Map(
            "name" -> value.name.getOrElse(UNSPECIFIED),
            "phone" -> value.phone.getOrElse(UNSPECIFIED),
            "keeper" -> (if (value.keeper.getOrElse(false)) "yes" else "no"),
            "date" -> value.availabilityMessage.map(_.toLocalDate.toString()).getOrElse(UNSPECIFIED),
            "time" -> value.time.getOrElse(UNSPECIFIED),
            "days" -> value.days.getOrElse(UNSPECIFIED),
            "telegram" -> value.telegram.getOrElse(UNSPECIFIED),
            "volunteer" -> (if (value.medic.getOrElse(false)) "medical" else "other")
          ))
        })

  }
}