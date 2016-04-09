package uk.gov.homeoffice.mongo.casbah

import scala.util.Try
import com.mongodb.ServerAddress
import com.mongodb.casbah.MongoDB
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AroundEach
import org.specs2.specification.core.Fragments

/**
 * Mix in this trait (at specification level) to provide a connection to an embedded Mongo for testing.
 * An embedded Mongo is started for each specification and examples within a specification will be run sequentially to allow for database clearance, avoiding any test interference.
 */
@deprecated(message = "Deprecated in favour of Embedded Mongo, the same idea as this trait but used at example level instead of specification", since = "8th April 2016")
trait EmbeddedMongoSpecification extends EmbeddedMongoExecutable with EmbeddedMongoClient {
  this: SpecificationLike =>

  sequential

  override def map(fs: => Fragments): Fragments = startMongo ^ fs ^ stopMongo

  private def startMongo = step {
    println("Starting Mongo")
    mongodExecutable.start()
  }

  private def stopMongo = step {
    println("Stopping Mongo")
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