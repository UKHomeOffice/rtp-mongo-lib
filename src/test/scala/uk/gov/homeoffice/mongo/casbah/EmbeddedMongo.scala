package uk.gov.homeoffice.mongo.casbah

import com.mongodb.ServerAddress
import com.mongodb.casbah.MongoDB
import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.Scope
import de.flapdoodle.embed.mongo.config.{MongodConfigBuilder, Net, RuntimeConfigBuilder}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{Command, MongodStarter}
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.runtime.Network._
import uk.gov.homeoffice.specs2.ComposableAround

trait EmbeddedMongo extends Scope with ComposableAround with MongoClient {
  lazy val network: Net = {
    def freeServerPort: Int = {
      val port = getFreeServerPort

      if ((27017 to 27027) contains port) freeServerPort
      else port
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

  mongodExecutable.start()
  println(s"Started Mongo running on ${network.getPort}")

  override def around[R: AsResult](r: => R): Result = {
    try {
      super.around(r)
    } finally {
      println("Stopping Mongo")
      mongodExecutable.stop()
    }
  }
}

trait MongoClient {
  self: EmbeddedMongo =>

  lazy val database = "embedded-database"

  lazy val mongoClient = com.mongodb.casbah.MongoClient(new ServerAddress(network.getServerAddress, network.getPort))

  lazy val db = mongoClient(database)

  trait TestMongo extends Mongo {
    lazy val db: MongoDB = self.db
  }
}