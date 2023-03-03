package de.bot.db.dao

import de.bot.db.common.MongoAccessHolder
import de.bot.db.common.QueryUtil._
import de.bot.db.model.{AuthToken, CustomDateFormatter}
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import play.api.libs.json._
import reactivemongo.api._
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.play.json.compat._
import reactivemongo.play.json.compat.bson2json._

import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AuthTokenDao @Inject()(val mongoAccess: MongoAccessHolder)(implicit ec: ExecutionContext) extends CustomDateFormatter {

  private val db = "voluntary-bot"
  private val collection = "token"
  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val mongoCollection: Future[BSONCollection] =
    mongoAccess.writeAccess.collectionAccess(db, collection)

  /**
   * Finds a token by its ID.
   * @param id The unique token ID.
   * @return The found token or None if no token for the given ID could be found.
   */
  def find(id: UUID): Future[Option[AuthToken]] = {
    val query = Json.obj("id" -> id)
    mongoCollection.findOne(query).map(_.flatMap(e => (e: JsObject).asOpt[AuthToken]))
  }

  /**
   * Finds expired tokens.
   * @param dateTime The current date time.
   */
  def findExpired(dateTime: DateTime): Future[Seq[AuthToken]] = {
    val query = Json.obj("expiry" -> Json.obj("$lt" -> Json.toJson(dateTime)))
    mongoCollection.findMany(query).map(_.flatMap(e => (e: JsObject).asOpt[AuthToken]).toSeq)
  }

  /**
   * Saves a token.
   * @param token The token to save.
   * @return The saved token.
   */
  def save(token: AuthToken): Future[AuthToken] = {

    mongoCollection.save(Json.toJsObject(token), writeConcern = WriteConcern.Acknowledged).map(_ => token)
  }

  /**
   * Removes the token for the given ID.
   * @param id The ID for which the token should be removed.
   * @return A future to wait for the process to be completed.
   */
  def remove(id: UUID): Future[Unit] = {
    val query = Json.obj("id" -> id)
    mongoCollection.removeMany(query).map(_ => ())
  }
}
