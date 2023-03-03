package de.bot.db.dao

import com.google.common.cache.CacheBuilder
import de.bot.db.common.MongoAccessHolder
import de.bot.db.common.QueryUtil._
import de.bot.db.model._
import org.slf4j.LoggerFactory
import reactivemongo.api.{Cursor, WriteConcern}
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.collection.BSONCollection

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MessageDao @Inject()(mongoAccess: MongoAccessHolder)(implicit ec: ExecutionContext) {

  val db = "voluntary-bot"
  val collection = "volunteer-info"
  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val mongoCollection: Future[BSONCollection] =
    mongoAccess.writeAccess.collectionAccess(db, collection)

  def save(parsedData: ParsedData) = {
    ParsedData.format.writeOpt(parsedData) match {
      case Some(data) => mongoCollection.save(data, writeConcern = WriteConcern.Acknowledged).map(_ => ())
      case _ => Future.successful(logger.error("Ups can't serialize parsedData object to db object"))
    }
  }

  def update(parsedData: ParsedData) = {
    ParsedData.format.writeOpt(parsedData) match {
      case Some(data) => mongoCollection.findFirstAndModify(BSONDocument("_id"-> parsedData.telegramUserId), BSONDocument("$set" -> data)).map(_ => ())
      case _ => Future.successful(logger.error("Ups can't serialize parsedData object to db object during update"))
    }
  }

  def findById(id: String) = {
    mongoCollection.findOneById(id).map{
      _ map (data => ParsedData.format.readOpt(data).getOrElse(throw new Exception("Can't parse db to model")))
    }
  }

  def getAll = {
    mongoCollection.flatMap(
      _.find(BSONDocument.empty)
        .cursor[BSONDocument]()
        .collect[List](-1, Cursor.FailOnError[List[BSONDocument]]())).map{
      _.flatMap(ParsedData.format.readOpt)
    }
  }



}

object MessageDao {

  private val logger = LoggerFactory.getLogger(getClass)

  val cache = CacheBuilder
    .newBuilder()
    .initialCapacity(1000)
    .maximumSize(2000)
    .expireAfterWrite(10, TimeUnit.MINUTES)
    .build[String, CommonMessage]()

  def toIdFormat(telegramId:Long, id: String) = telegramId+"_"+id

  def parseName(msg: CommonMessage) = {
    msg match {
      case NameMessage(text) => text
      case _ => throw new Exception("Try to parse name but data is not looks like name")
    }
  }

  def parsePhone(msg: CommonMessage) = {
    msg match {
      case PhoneMessage(text) => text
      case _ => throw new Exception("Try to parse phone but data is not looks like phone")
    }
  }

  def parseKeeper(msg: CommonMessage) = {
    msg match {
      case SecurityInfoMessage(text) => text
      case _                         => throw new Exception("Try to parse keeper but data is not looks like keeper")
    }
  }

  def parseMedic(msg: CommonMessage) = {
    msg match {
      case DoctorInfoMessage(text) => text
      case _ => throw new Exception("Try to parse medic but data is not looks like medic")
    }
  }

  def parseStart(msg: CommonMessage) = {
    msg match {
      case StarDateMessage(start) => start
      case _ => throw new Exception("Try to parse start but data is not looks like start")
    }
  }

  def parseDay(msg: CommonMessage) = {
    msg match {
      case PreferableDays(days) => days
      case _ => throw new Exception("Try to parse days but data is not looks like days")
    }
  }

  def parseTime(msg: CommonMessage) = {
    msg match {
      case TimeMessage(txt) => txt
      case _ => throw new Exception("Try to parse time but data is not looks like time")
    }
  }

  def prepareResult(telegram: Option[String], idOpt: Option[Long]) = {

    for{
      id <- idOpt
    } yield {
      val name = Option(cache.getIfPresent(toIdFormat(id, "name"))).map(parseName)
      val phone = Option(cache.getIfPresent(toIdFormat(id, "phone"))).map(parsePhone)
      val keeper = Option(cache.getIfPresent(toIdFormat(id, "keeper"))).map(parseKeeper)
      val start = Option(cache.getIfPresent(toIdFormat(id, "date"))).map(parseStart)
      val days = Option(cache.getIfPresent(toIdFormat(id, "days"))).map(parseDay)
      val time = Option(cache.getIfPresent(toIdFormat(id, "time"))).map(parseTime)
      val medic = Option(cache.getIfPresent(toIdFormat(id, "medic"))).map(parseMedic)
      val res = ParsedData(id.toString, telegram, name, phone,keeper, medic, start, days, time)
      res
    }



  }

}
