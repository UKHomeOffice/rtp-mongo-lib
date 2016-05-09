package uk.gov.homeoffice.mongo.casbah

import com.mongodb.ServerAddress
import com.mongodb.casbah.{MongoClient, MongoDB}
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AroundEach

/**
 * Mix in this trait (at specification level) to provide a connection to an embedded Mongo for testing.
 * Every running example will be given its own unique instance of Mongo.
 * If you can, it is recommended to use EmbeddedMongo instead, as it is mixed in at the example level and can aid code readability.
 */
trait EmbeddedMongoSpecification extends AroundEach with Mongo with EmbeddedMongoExecutable {
  spec: SpecificationLike =>

  isolated

  lazy val database = "embedded-database"

  lazy val mongoClient = MongoClient(new ServerAddress(network.getServerAddress, network.getPort))

  lazy val db = mongoClient(database)

  override def around[T: AsResult](t: => T): Result = try {
    debug("Starting Mongo...")
    mongodExecutable.start()
    AsResult(t)
  } finally {
    debug("Stopping Mongo")
    mongodExecutable.stop()
  }

  trait TestMongo extends Mongo {
    lazy val db: MongoDB = spec.db
  }
}