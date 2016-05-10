package uk.gov.homeoffice.mongo.casbah

import java.util.concurrent.TimeUnit.{MILLISECONDS, SECONDS}
import com.mongodb.ServerAddress
import com.mongodb.casbah.{MongoClient, MongoDB}
import de.flapdoodle.embed.mongo.config.{MongodConfigBuilder, Net, RuntimeConfigBuilder}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{Command, MongodStarter}
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.runtime.Network._
import grizzled.slf4j.Logging

trait EmbeddedMongoExecutable extends Logging {
  executable =>

  lazy val network: Net = {
    def freeServerPort: Int = {
      val port = getFreeServerPort

      // Avoid standard Mongo ports in case a standalone Mongo is running.
      if ((27017 to 27027) contains port) {
        MILLISECONDS.sleep(10)
        freeServerPort
      } else {
        port
      }
    }

    new Net(freeServerPort, localhostIsIPv6)
  }

  lazy val mongodConfig = new MongodConfigBuilder()
    .version(Version.Main.PRODUCTION)
    .net(network)
    .build

  lazy val runtimeConfig = new RuntimeConfigBuilder()
    .defaults(Command.MongoD)
    .processOutput(ProcessOutput.getDefaultInstanceSilent)
    .build()

  lazy val runtime = MongodStarter.getInstance(runtimeConfig)

  lazy val mongodExecutable = runtime.prepare(mongodConfig)

  lazy val database = "embedded-database"

  lazy val mongoClient = MongoClient(new ServerAddress(network.getServerAddress, network.getPort))

  lazy val db = mongoClient(database)

  def startMongo(): Unit = {
    def startMongo(attempt: Int, sleepTime: Int = 2): Unit = try {
      mongodExecutable.start()
      info(s"Started Mongo running on ${network.getPort}")
      SECONDS.sleep(1)
    } catch {
      case t: Throwable =>
        println(s"Failed to start Mongo on attempt number $attempt")
        val nextAttempt = attempt + 1

        if (nextAttempt <= 5) {
          SECONDS.sleep(sleepTime)
          startMongo(nextAttempt, sleepTime + 1)
        } else {
          throw new Exception("Failed to start Mongo after 5 attempts", t)
        }
    }

    startMongo(1)
  }

  def stopMongo(): Unit = {
    info(s"Stopping Mongo running on ${network.getPort}")
    mongodExecutable.stop()
    SECONDS.sleep(1)
  }

  trait TestMongo extends Mongo {
    lazy val db: MongoDB = executable.db
  }
}