package uk.gov.homeoffice.mongo

import com.typesafe.scalalogging.StrictLogging
import org.mongodb.scala.MongoClient.DEFAULT_CODEC_REGISTRY
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.{ConnectionString, MongoClient, MongoClientSettings, MongoDatabase, WriteConcern}

import scala.reflect.ClassTag

case class MongoConnection(
  connectionString :String,
  settings :MongoClientSettings,
  client :MongoClient,
  database :MongoDatabase
) {
  def mongoCollection[T : ClassTag](collectionName :String) = database.getCollection[T](collectionName)
}

object MongoConnector extends StrictLogging {

  /* MongoConnector.connect opens a new database connection. You should either pass the one instance around,
   * or assign it to a global, or close it meticulously with connector.database.close() */

  def connect(connectionString :String, appName :String, ssl :Boolean, databaseName :String) :MongoConnection = {

    logger.info (s"Connecting to Mongo using connection string: ${connectionString.replaceAll(":.*@",":*****@")}")

    val settings = MongoClientSettings.builder
      .applicationName(appName)
      .codecRegistry(DEFAULT_CODEC_REGISTRY)
      .writeConcern(WriteConcern.ACKNOWLEDGED)
      .applyConnectionString(new ConnectionString(connectionString))
      .applyToSslSettings(b => b.enabled(ssl))
      .build()

    val client: MongoClient = MongoClient(settings)

    val database = client.getDatabase(databaseName)

    logger.info (s"Connected to mongo db: ${database.name}")

    MongoConnection(
      connectionString,
      settings,
      client,
      database
    )
  }
}
