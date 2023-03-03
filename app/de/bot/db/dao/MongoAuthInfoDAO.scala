package de.bot.db.dao

import com.mohiva.play.silhouette.api.{AuthInfo, LoginInfo}
import com.mohiva.play.silhouette.persistence.daos.DelegableAuthInfoDAO
import de.bot.db.common.MongoAccess
import play.api.libs.json.{Format, JsObject, Json}
import reactivemongo.api.bson.BSONDocumentHandler
import reactivemongo.api.commands.WriteResult
import reactivemongo.play.json.compat.json2bson._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

@Singleton
class MongoAuthInfoDAO[A <: AuthInfo] @Inject()(mongoAccess: MongoAccess)(implicit val classTag: ClassTag[A], ec: ExecutionContext, bFormat: BSONDocumentHandler[A], format: Format[A])
  extends DelegableAuthInfoDAO[A] {

  private val authInfoName = classTag.runtimeClass.getSimpleName

  /**
   * The name of the collection to store the auth info.
   */

  /**
   * The collection to use for JSON queries.
   */
  private val jsonCollection = mongoAccess.collectionAccess("voluntary-bot", authInfoName)

  /**
   * Finds the auth info which is linked with the specified login info.
   * @param loginInfo The linked login info.
   * @return The retrieved auth info or None if no auth info could be retrieved for the given login info.
   */
  def find(loginInfo: LoginInfo) = {
    jsonCollection.flatMap(_.find(Json.obj("_id" -> loginInfo)).projection(Json.obj("_id" -> 0)).one[A])
  }

  /**
   * Adds new auth info for the given login info.
   * @param loginInfo The login info for which the auth info should be added.
   * @param authInfo  The auth info to add.
   * @return The added auth info.
   */
  def add(loginInfo: LoginInfo, authInfo: A) = {
    onSuccess(jsonCollection.flatMap(_.insert.one(merge(loginInfo, authInfo))), authInfo)
  }

  /**
   * Updates the auth info for the given login info.
   * @param loginInfo The login info for which the auth info should be updated.
   * @param authInfo  The auth info to update.
   * @return The updated auth info.
   */
  def update(loginInfo: LoginInfo, authInfo: A) = {
    updated(jsonCollection.flatMap(_.update.one(Json.obj("_id" -> loginInfo), merge(loginInfo, authInfo)))).map {
      case num if num > 0 => authInfo
      case _              => throw new Exception(s"Could not update $authInfoName for login info: " + loginInfo)
    }
  }

  /**
   * Saves the auth info for the given login info.
   *
   * This method either adds the auth info if it doesn't exists or it updates the auth info
   * if it already exists.
   * @param loginInfo The login info for which the auth info should be saved.
   * @param authInfo  The auth info to save.
   * @return The saved auth info.
   */
  def save(loginInfo: LoginInfo, authInfo: A) = {
    val l = Json.obj("_id" -> loginInfo)
    onSuccess(jsonCollection.flatMap(_.update.one(l, merge(loginInfo, authInfo), upsert = true)), authInfo)
  }

  /**
   * Removes the auth info for the given login info.
   * @param loginInfo The login info for which the auth info should be removed.
   * @return A future to wait for the process to be completed.
   */
  def remove(loginInfo: LoginInfo) = onSuccess(jsonCollection.flatMap(_.delete.one(Json.obj("_id" -> loginInfo))), ())

  /**
   * Merges the [[LoginInfo]] and the [[AuthInfo]] into one Json object.
   * @param loginInfo The login info to merge.
   * @param authInfo  The auth info to merge.
   * @return A Json object consisting of the [[LoginInfo]] and the [[AuthInfo]].
   */
  private def merge(loginInfo: LoginInfo, authInfo: A) =
    Json.obj("_id" -> loginInfo).deepMerge(Json.toJson(authInfo).as[JsObject])

  /**
   * Returns some result on success and None on error.
   * @param result The last result.
   * @param entity The entity to return.
   * @tparam T The type of the entity.
   * @return The entity on success or an exception on error.
   */
  private def onSuccess[T](result: Future[WriteResult], entity: T): Future[T] = result.recoverWith {
    case e => Future.failed(new Exception("Got exception from MongoDB", e.getCause))
  }.map { r =>
    WriteResult.Exception.unapply(r) match {
      case Some(e) => throw new Exception(e.message, e)
      case _       => entity
    }
  }

  /**
   * Returns the number of updated documents on success and None on error.
   * @param result The last result.
   * @return The number of updated documents on success or an exception on error.
   */
  private def updated(result: Future[WriteResult]): Future[Int] = result.recoverWith {
    case e => Future.failed(new Exception("Got exception from MongoDB", e.getCause))
  }.map { r =>
    WriteResult.Exception.unapply(r) match {
      case Some(e) => throw new Exception(e.message, e)
      case _       => r.n
    }
  }
}
