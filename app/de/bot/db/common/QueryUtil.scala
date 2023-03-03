package de.bot.db.common

import org.slf4j.LoggerFactory
import reactivemongo.api.{Cursor, WriteConcern}
import reactivemongo.api.bson.{BSONDocument, BSONObjectID, BSONString}
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.play.json.compat._
import reactivemongo.play.json.compat.bson2json._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object QueryUtil {

  val MONGO_ID_FIELD = "_id"
  private val logger = LoggerFactory.getLogger(getClass)

  implicit class CollectionExtension(collection: Future[BSONCollection]) {

    def findOneById(id: String)(implicit ec: ExecutionContext): Future[Option[BSONDocument]] =
      findOne(BSONDocument(MONGO_ID_FIELD -> BSONString(id)))

    def findOneByObjectId(id: BSONObjectID)(implicit ec: ExecutionContext): Future[Option[BSONDocument]] =
      BSONObjectID.parse(id.toString) match {
        case Success(value) => findOne(BSONDocument(MONGO_ID_FIELD -> value))
        case Failure(exception) =>
          logger.warn("Problem with querying data by ObjectId: " + exception.getMessage)
          Future.successful(None)
      }

    def findOne(
      query: BSONDocument,
      projection: Option[BSONDocument] = None,
      hintOp: Option[BSONDocument] = None,
      sortBy: Option[BSONDocument] = None,
      skip: Option[Int] = None,
      explain: Option[Boolean] = None
    )(implicit ec: ExecutionContext): Future[Option[BSONDocument]] = {

      val baseQ = collection.map { coll =>
        hintOp match {
          case Some(v) => coll.find(query, projection).hint(coll.hint(v))
          case None    => coll.find(query, projection)
        }
      }

      val skipped = skip match {
        case Some(v) => baseQ.map(_.skip(v))
        case _       => baseQ
      }

      val sorted = sortBy match {
        case Some(v) => skipped.map(_.sort(v))
        case _       => skipped
      }

      val withExplain = explain match {
        case Some(v) => sorted.map(_.explain(v))
        case _       => sorted
      }

      withExplain.flatMap(_.one[BSONDocument])
    }

    def findFirstAndModify(
      query: BSONDocument,
      update: BSONDocument,
      sortBy: Option[BSONDocument] = None,
      projection: Option[BSONDocument] = None,
      isUpsert: Boolean = true
    )(implicit ec: ExecutionContext): Future[Option[BSONDocument]] =
      collection.flatMap { coll =>
        val updateOp = coll.updateModifier(update, upsert = isUpsert)
        coll.findAndModify(query, updateOp, sort = sortBy, fields = projection).map(_.result[BSONDocument])
      }

    def getCursor(
      query: BSONDocument,
      projection: Option[BSONDocument] = None,
      sortBy: Option[BSONDocument] = None
    )(implicit ec: ExecutionContext): Future[Cursor[BSONDocument]] = {

      val withProjection = collection.map(_.find(query, projection))

      val sorted = sortBy match {
        case Some(v) => withProjection.map(_.sort(v))
        case _       => withProjection
      }

      sorted.map {
        _.cursor[BSONDocument]()
      }
    }

    def findMany[T](
      query: BSONDocument,
      projection: Option[BSONDocument] = None,
      hintOp: Option[BSONDocument] = None,
      limit: Option[Int] = None,
      sortBy: Option[BSONDocument] = None,
      skip: Option[Int] = None,
      explain: Option[Boolean] = None
    )(
      implicit ec: ExecutionContext
    ): Future[Iterator[BSONDocument]] = {
      val baseQ = collection.map { coll =>
        hintOp match {
          case Some(v) => coll.find(query, projection).hint(coll.hint(v))
          case None    => coll.find(query, projection)
        }
      }

      val skipped = skip match {
        case Some(v) => baseQ.map(_.skip(v))
        case _       => baseQ
      }

      val sorted = sortBy match {
        case Some(v) => skipped.map(_.sort(v))
        case _       => skipped
      }

      val withExplain = explain match {
        case Some(v) => sorted.map(_.explain(v))
        case _       => sorted
      }

      withExplain.flatMap {
        _.cursor[BSONDocument]()
          .collect[Iterator](limit.getOrElse(-1), Cursor.FailOnError[Iterator[BSONDocument]]())
      }
    }

    def countBy(query: BSONDocument)(implicit ec: ExecutionContext): Future[Long] =
      collection.flatMap(_.count(Some(query)))

    def removeOneById(id: String)(implicit ec: ExecutionContext): Future[WriteResult] =
      collection.flatMap(_.delete.one(BSONDocument(MONGO_ID_FIELD -> id)))

    def removeMany(doc: BSONDocument)(implicit ec: ExecutionContext): Future[WriteResult] =
      collection.flatMap(_.delete.one(doc))

    def updateOneByQuery(
      find: BSONDocument,
      update: BSONDocument,
      upsert: Boolean = true,
      multi: Boolean = false,
      wc: WriteConcern
    )(
      implicit ec: ExecutionContext
    ): Future[WriteResult] =
      collection.flatMap(
        _.update(writeConcern = wc).one(
          find,
          update,
          upsert = upsert,
          multi = multi
        )
      )

    def updateManyByQuery(
      find: BSONDocument,
      update: BSONDocument,
      wc: WriteConcern
    )(
      implicit ec: ExecutionContext
    ): Future[WriteResult] =
      collection.flatMap(
        _.update(writeConcern = wc).one(
          find,
          update,
          multi = true
        )
      )

    def updateOneById(find: BSONDocument, update: BSONDocument, upsert: Boolean = true, multi: Boolean = false)(
      implicit ec: ExecutionContext
    ): Future[WriteResult] = {
      require(find.toMap.size == 1, "find query is not by mongo ID")
      require(find.toMap.contains(MONGO_ID_FIELD), "find query is not by mongo ID")

      collection.flatMap(
        _.update.one(
          find,
          update,
          upsert = upsert,
          multi = multi
        )
      )
    }

    def save(doc: BSONDocument, writeConcern: WriteConcern)(implicit ec: ExecutionContext): Future[WriteResult] = {
      val docId = doc.getAsOpt[String](MONGO_ID_FIELD)
      collection.flatMap { col =>
        if (docId.nonEmpty) {
          col.update(writeConcern).one(q = BSONDocument(MONGO_ID_FIELD -> docId.get), u = doc, upsert = true)
        } else {
          col.insert(writeConcern).one(doc)
        }
      }
    }

  }

  implicit class BsonDocumentExtension(dbData: Future[Option[BSONDocument]]) {

    def mapToScala[T](transformer: BSONDocument => T)(implicit ec: ExecutionContext): Future[Option[T]] =
      dbData.map(_.map(transformer))

  }
}
