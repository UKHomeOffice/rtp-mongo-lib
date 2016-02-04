package uk.gov.homeoffice.mongo.casbah

import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AroundEach
import org.specs2.specification.core.Fragments
import com.mongodb.ServerAddress
import com.mongodb.casbah.{MongoClient, MongoDB}
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.{MongodConfigBuilder, Net}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network._

/**
 * Mix in this trait to provide a connection to an embedded Mongo for testing.
 * An embedded Mongo is started for each specification and examples within a specification will be run sequentially to allow for database clearance, avoiding any test interference.
 */
trait EmbeddedMongoSpec extends EmbeddedMongoClient {
  this: SpecificationLike =>

  sequential

  lazy val network = new Net(getFreeServerPort, localhostIsIPv6)

  lazy val mongodConfig = new MongodConfigBuilder()
    .version(Version.Main.PRODUCTION)
    .net(network)
    .build

  lazy val runtime = MongodStarter.getDefaultInstance

  lazy val mongodExecutable = runtime.prepare(mongodConfig)

  override def map(fs: => Fragments): Fragments = startMongo ^ fs ^ stopMongo

  private def startMongo = step {
    println(s"===> Starting embedded Mongo ${network.getServerAddress}:${network.getPort}")
    mongodExecutable.start()
  }

  private def stopMongo = step {
    println(s"===> Stopping embedded Mongo ${network.getServerAddress}:${network.getPort}")
    mongodExecutable.stop()
  }
}

trait EmbeddedMongoClient extends AroundEach {
  this: EmbeddedMongoSpec =>

  lazy val mongoClient = MongoClient(new ServerAddress(network.getServerAddress, network.getPort))

  lazy val mongodb = mongoClient("embedded-database")

  override protected def around[R: AsResult](r: => R): Result = try {
    dropDatabase()
    AsResult(r)
  } finally {
    dropDatabase()
  }

  private def dropDatabase() = mongoClient getDatabaseNames() map {
    mongoClient.getDB
  } foreach {
    _.dropDatabase()
  }

  trait TestMongo extends Mongo {
    lazy val db: MongoDB = mongodb
  }
}