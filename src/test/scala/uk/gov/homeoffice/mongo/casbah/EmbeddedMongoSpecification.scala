package uk.gov.homeoffice.mongo.casbah

import scala.util.Try
import com.mongodb.ServerAddress
import com.mongodb.casbah.MongoDB
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AroundEach

/**
 * Mix in this trait (at specification level) to provide a connection to an embedded Mongo for testing.
 * Every running example will be given its own unique instance of Mongo.
 * If you can, it is recommended to use EmbeddedMongo instead, as it is mixed in at the example level and can aid code readability.
 */
trait EmbeddedMongoSpecification extends AroundEach with EmbeddedMongoExecutable with EmbeddedMongoClient {
  this: SpecificationLike =>

  isolated

  override def around[T: AsResult](t: => T): Result = try {
    debug("Starting Mongo...")
    mongodExecutable.start()
    AsResult(t)
  } finally {
    debug("Stopping Mongo")
    mongodExecutable.stop()
  }
}

trait EmbeddedMongoClient extends Mongo with AroundEach {
  client: EmbeddedMongoSpecification =>

  lazy val database = "embedded-database"

  lazy val mongoClient = com.mongodb.casbah.MongoClient(new ServerAddress(network.getServerAddress, network.getPort))

  lazy val db = mongoClient(database)

  override protected def around[R: AsResult](r: => R): Result = try {
    dropDatabase()
    AsResult(r)
  } finally {
    dropDatabase()
  }

  private def dropDatabase() = Try {
    mongoClient getDatabaseNames()
  } map {
    _.map {
      mongoClient.getDB
    } foreach {
      _.dropDatabase()
    }
  }

  trait TestMongo extends Mongo {
    lazy val db: MongoDB = client.db
  }
}