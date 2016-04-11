package uk.gov.homeoffice.mongo.casbah

import java.util.concurrent.TimeUnit
import com.mongodb.ServerAddress
import com.mongodb.casbah.MongoDB
import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.Scope
import uk.gov.homeoffice.specs2.ComposableAround

/**
  * Mix in this trait (at example level) to provide a connection to an embedded Mongo for testing.
  * An embedded Mongo is started for each example.
  */
trait EmbeddedMongo extends Scope with ComposableAround with EmbeddedMongoExecutable with MongoClient {
  startMongo()

  def startMongo(): Unit = {
    def startMongo(attempt: Int): Unit = try {
      mongodExecutable.start()
      println(s"Started Mongo running on ${network.getPort}")
    } catch {
      case t: Throwable =>
        println(s"Failed to start Mongo on attempt number $attempt")
        val nextAttempt = attempt + 1

        if (nextAttempt <= 5) {
          TimeUnit.SECONDS.sleep(2)
          startMongo(nextAttempt)
        } else {
          throw new Exception("Failed to start Mongo after 5 attempts", t)
        }
    }

    startMongo(1)
  }

  override def around[R: AsResult](r: => R): Result = {
    try {
      super.around(r)
    } finally {
      println("Stopping Mongo")
      mongodExecutable.stop()
    }
  }
}

trait MongoClient extends Mongo {
  self: EmbeddedMongo =>

  lazy val database = "embedded-database"

  lazy val mongoClient = com.mongodb.casbah.MongoClient(new ServerAddress(network.getServerAddress, network.getPort))

  lazy val db = mongoClient(database)

  trait TestMongo extends Mongo {
    lazy val db: MongoDB = self.db
  }
}