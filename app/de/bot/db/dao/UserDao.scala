package de.bot.db.dao

import com.mohiva.play.silhouette.api.LoginInfo
import de.bot.db.common.MongoAccessHolder
import de.bot.db.common.QueryUtil._
import de.bot.db.model.{AuthToken, User}
import org.slf4j.LoggerFactory
import play.api.libs.json._
import reactivemongo.api.WriteConcern
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.play.json.compat._

import java.util.UUID
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UserDao @Inject()(val mongoAccess: MongoAccessHolder)(implicit ec: ExecutionContext) {

  private val db = "voluntary-bot"
  private val collection = "user"
  private val logger = LoggerFactory.getLogger(getClass)

  private lazy val mongoCollection: Future[BSONCollection] =
    mongoAccess.writeAccess.collectionAccess(db, collection)

  /**
   * Finds a user by its login info.
   * @param loginInfo The login info of the user to find.
   * @return The found user or None if no user for the given login info could be found.
   */
  def find(loginInfo: LoginInfo): Future[Option[User]] = {
    val query = Json.obj("loginInfo" -> loginInfo)
    mongoCollection.findOne(query).map(_.flatMap(e => (e: JsObject).asOpt[User]))
  }

  /**
   * Finds a user by its user ID.
   * @param userID The ID of the user to find.
   * @return The found user or None if no user for the given ID could be found.
   */
  def find(userID: UUID): Future[Option[User]] = {
    val query = Json.obj("userID" -> userID)
    mongoCollection.findOne(query).map(_.flatMap(e => (e: JsObject).asOpt[User]))
  }

  /**
   * Saves a user.
   * @param user The user to save.
   * @return The saved user.
   */
  def save(user: User): Future[User] = {
    mongoCollection.updateOneByQuery(Json.obj("userID" -> user.userID), Json.toJsObject(user), wc = WriteConcern.Acknowledged).map(_ => user)
  }
}
