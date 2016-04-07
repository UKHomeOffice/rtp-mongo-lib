package uk.gov.homeoffice.mongo.casbah

import scala.util.Try
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AroundEach
import org.specs2.specification.core.Fragments
import com.mongodb.ServerAddress
import com.mongodb.casbah.{MongoClient, MongoDB}
import de.flapdoodle.embed.mongo.config.{MongodConfigBuilder, Net, RuntimeConfigBuilder}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{Command, MongodStarter}
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.runtime.Network._

/**
 * Mix in this trait to provide a connection to an embedded Mongo for testing.
 * An embedded Mongo is started for each specification and examples within a specification will be run sequentially to allow for database clearance, avoiding any test interference.
 */
trait EmbeddedMongoSpecification extends EmbeddedMongoClient {
  this: SpecificationLike =>

  sequential

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

  lazy val mongoClient = MongoClient(new ServerAddress(network.getServerAddress, network.getPort))

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