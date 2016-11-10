package uk.gov.homeoffice.mongo.casbah

import java.util
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit._
import com.mongodb.ServerAddress
import com.mongodb.casbah.MongoDB
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.SpecificationLike
import de.flapdoodle.embed.mongo.config.{MongodConfigBuilder, Net, RuntimeConfigBuilder}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{Command, MongodStarter}
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.runtime.Network._
import grizzled.slf4j.Logging

object EmbeddedMongoSpecification {
  private val ports = Collections.synchronizedSet(new util.HashSet[Int])

  lazy val runtimeConfig = new RuntimeConfigBuilder()
    .defaults(Command.MongoD)
    .processOutput(ProcessOutput.getDefaultInstanceSilent)
    .build()

  lazy val runtime = MongodStarter getInstance runtimeConfig
}

/**
 * Mix in this trait (at specification level) to provide a connection to an embedded Mongo for testing.
 * Every running example will be given its own unique instance of Mongo.
 */
trait EmbeddedMongoSpecification extends MongoSpecification with Logging {
  self: SpecificationLike =>

  import EmbeddedMongoSpecification._

  isolated
  sequential

  lazy val network: Net = {
    def freeServerPort: Int = {
      val port = getFreeServerPort

      // Avoid standard Mongo ports in case a standalone Mongo is running.
      if ((27017 to 27027) contains port) {
        MILLISECONDS.sleep(10)
        freeServerPort
      } else {
        if (ports.add(port)) {
          info(s"Mongo ports in use: $ports")
          port
        } else {
          freeServerPort
        }
      }
    }

    new Net(freeServerPort, localhostIsIPv6)
  }

  lazy val mongodConfig = new MongodConfigBuilder()
    .version(Version.Main.PRODUCTION)
    .net(network)
    .build

  lazy val mongodExecutable = runtime prepare mongodConfig

  override lazy val database = "embedded-database"

  override lazy val mongoClient = com.mongodb.casbah.MongoClient(new ServerAddress(network.getServerAddress, network.getPort))

  override lazy val mongoDB = mongoClient(database)

  override def around[R: AsResult](r: => R): Result = try {
    startMongo()
    AsResult(r)
  } finally {
    stopMongo()
  }

  def startMongo(): Unit = {
    def startMongo(attempt: Int, sleepTime: Int = 2): Unit = try {
      mongodExecutable.start()
      info(s"Started Mongo running on ${network.getPort}")
      waitForMongo
    } catch {
      case t: Throwable =>
        println(s"Failed to start Mongo on attempt number $attempt")
        val nextAttempt = attempt + 1

        if (nextAttempt <= 10) {
          SECONDS.sleep(sleepTime)
          startMongo(nextAttempt, sleepTime + 1)
        } else {
          throw new Exception("Failed to start Mongo after 10 attempts", t)
        }
    }

    def waitForMongo: Boolean = {
      val mongoRunning = mongoDB.command("serverStatus").ok

      if (!mongoRunning) {
        SECONDS.sleep(1)
        waitForMongo
      }

      mongoRunning
    }

    startMongo(1)
  }

  def stopMongo(): Unit = {
    info(s"Stopping Mongo running on ${network.getPort}")
    mongodExecutable.stop()
    TimeUnit.SECONDS.sleep(2)
    ports.remove(network.getPort)
  }

  trait TestMongo extends Mongo {
    lazy val mongoDB: MongoDB = self.mongoDB
  }
}