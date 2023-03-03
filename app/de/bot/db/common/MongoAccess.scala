package de.bot.db.common

import org.slf4j.LoggerFactory
import play.api.{Configuration, Play}
import reactivemongo.api._
import reactivemongo.api.bson.collection.BSONCollection

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

case class MongoRSConfig(host: String, port: Int) {
  override def toString() = s"$host:$port"
}

@Singleton
class MongoAccess @Inject()(implicit executionContext: ExecutionContext, configuration: Configuration) {

  val rsMembers = configuration.get[Seq[Configuration]]("mongo.rsMembers").map{
    el =>
      MongoRSConfig(el.get[String]("host"), el.get[Int]("port"))
  }

  val connectTimeout: Int = 10000
  val numConnections: Int = 10

  val driver = new reactivemongo.api.AsyncDriver()

  /**
   * Options for [[MongoConnection]]
   * (see [[http://reactivemongo.org/releases/0.1x/documentation/tutorial/connect-database.html#connection-options more documentation]]).
   */
  val conOpts = MongoConnectionOptions(
    /** The default [[https://docs.mongodb.com/manual/reference/write-concern/ write concern]] */
    writeConcern = WriteConcern.Acknowledged,
    /** The name of the database used for authentication */
    authenticationDatabase = MongoConnectionOptions.default.authenticationDatabase,
    /**
     * The number of milliseconds to wait for a connection
     * to be established before giving up.
     */
    connectTimeoutMS = connectTimeout,
    /**
     * The number of channels (connections)
     * per node (ReactiveMongo-specific option)
     */
    nbChannelsPerNode = numConnections, // # of connections allowed per host (pool size, per host) default 100
    /** The default read preference */
    readPreference = ReadPreference.Primary, // the read preference to use for queries, map-reduce, aggregation, and count
    /** Enable SSL connection (required to be accepted on server-side) */
    sslEnabled = false,
    /** Either [[ScramSha1Authentication]] or [[X509Authentication]] */
    authenticationMechanism = MongoConnectionOptions.default.authenticationMechanism,
    /**
     * If `sslEnabled` is true, this one indicates whether
     * to accept invalid certificates (e.g. self-signed).
     */
    sslAllowsInvalidCert = false,
    /**
     * TCP KeepAlive flag (ReactiveMongo-specific option).
     * The default value is false (see [[http://docs.oracle.com/javase/8/docs/api/java/net/StandardSocketOptions.html#SO_KEEPALIVE SO_KEEPALIVE]]).
     */
    keepAlive = false,
    /**
     * TCP NoDelay flag (ReactiveMongo-specific option).
     * The default value is false (see [[http://docs.oracle.com/javase/8/docs/api/java/net/StandardSocketOptions.html#TCP_NODELAY TCP_NODELAY]]).
     */
    tcpNoDelay = false,
    /** The maximum number of requests processed per channel */
    maxInFlightRequestsPerChannel = MongoConnectionOptions.default.maxInFlightRequestsPerChannel,
    /** The minimum number of idle channels per node */
    minIdleChannelsPerNode = MongoConnectionOptions.default.minIdleChannelsPerNode,
    /** The default failover strategy */
    failoverStrategy = FailoverStrategy.strict,
    /**
     * The interval in milliseconds used by monitor to refresh the node set
     * (default: 10000 aka 10s)
     */
    heartbeatFrequencyMS = MongoConnectionOptions.default.heartbeatFrequencyMS,
    /**
     * The maximum number of milliseconds that a
     * [[https://docs.mongodb.com/manual/reference/connection-string/#urioption.maxIdleTimeMS channel can remain idle]]
     * in the connection pool before being removed and closed (default: 0 to disable, as implemented using
     * [[http://netty.io/4.1/api/io/netty/handler/timeout/IdleStateHandler.html Netty IdleStateHandler]]);
     * If not 0, must be greater or equal to [[heartbeatFrequencyMS]].
     */
    maxIdleTimeMS = 0,
    /** The maximum size of the pool history (default: 25) */
    maxHistorySize = MongoConnectionOptions.default.maxHistorySize,
    /** The credentials per authentication database names */
    credentials = MongoConnectionOptions.default.credentials,
    /** An optional key store */
    keyStore = MongoConnectionOptions.default.keyStore,
    /** The default [[https://docs.mongodb.com/manual/reference/read-concern/ read concern]] */
    readConcern = MongoConnectionOptions.default.readConcern,
    /** An optional [[https://docs.mongodb.com/manual/reference/connection-string/#urioption.appName application name]] */
    appName = MongoConnectionOptions.default.appName
  )

  private val logger = LoggerFactory.getLogger(getClass)
  private val connection: Future[MongoConnection] =
    driver.connect(rsMembers.map(_.toString()).toList, options = conOpts)

  def dropDatabase(dbName: String): Future[Unit] = {
    logger.warn("Dropping database {}", dbName)
    database(dbName).flatMap(el => el.drop())
  }

  private def database(dbName: String): Future[DB] = connection.flatMap(_.database(dbName))

  private[db] def closeConnection(): Future[Unit] =
    connection.flatMap(_.close()(60.seconds).map(_ => (): Unit).recover {
      case NonFatal(e) =>
        logger.error("while closing write connections", e)
    })


  private[db] def collectionAccess(dbName: String, collectionName: String): Future[BSONCollection] =
    database(dbName).map(_.collection[BSONCollection](collectionName))
}

@Singleton
class MongoAccessHolder @Inject()(implicit executionContext: ExecutionContext, configuration: Configuration) {

  /**
   * The default mongo access.
   */
  private[db] lazy val writeAccess = {
    initializedWriteAccess = true
    logger.info("init writeAccess connection")
    new MongoAccess
  }

  private val logger = LoggerFactory.getLogger(getClass)
  private var initializedWriteAccess = false



}
