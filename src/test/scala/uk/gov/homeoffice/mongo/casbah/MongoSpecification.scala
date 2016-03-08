package uk.gov.homeoffice.mongo.casbah

import java.util.UUID
import scala.util.Try
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AroundEach
import com.mongodb.casbah.{MongoClient, MongoClientURI, MongoDB}
import uk.gov.homeoffice.configuration.{ConfigFactorySupport, HasConfig}

/**
 * Mix in this trait to provide a connection to Mongo for testing.
 * The functionality provided here requires Mongo to be running.
 * Every running example will be given its own unique database in Mongo - the database is created upon example execution and dropped when the example is complete.
 * By default the Mongo client managed by this trait, connects to a "default" Mongo i.e. 127.0.0.1 on port 27017 - these can be overwritten via configurations "mongo.host" and "mongo.port"
 * Note that there is an embedded mongo version of this trait, though the underlying "test" Mongo does not implement all of the Mongo API.
 */
trait MongoSpecification extends Mongo with AroundEach with HasConfig with ConfigFactorySupport with MongoRunning {
  spec: SpecificationLike =>

  isolated

  val server = config.text("mongo.host", "127.0.0.1")

  val port = config.int("mongo.port", 27017)

  val database = s"test-${UUID.randomUUID()}"

  val mongoConnectionUri = s"mongodb://$server:$port/$database"

  lazy val mongoClientURI = MongoClientURI(mongoConnectionUri)

  lazy val mongoClient = MongoClient(mongoClientURI)

  lazy val db = mongoClient(database)

  override def around[T: AsResult](t: => T): Result = if (mongoRunning) {
    try {
      debug(s"+ Created $database in spec $getClass")
      AsResult(t)
    } finally {
      debug(s"x Dropping $database")
      closeMongo
    }
  } else {
    AsResult(skipped("*** Mongo is not running!!! ***"))
  }

  override def finalize() = {
    closeMongo
    super.finalize()
  }

  def closeMongo = {
    Try { mongoClient.dropDatabase(database) }
    Try { mongoClient.close() }
  }

  trait TestMongo extends Mongo {
    lazy val db: MongoDB = spec.db
  }
}

trait MongoRunning {
  this: MongoSpecification =>

  def mongoRunning: Boolean = try {
    val collection = db.getCollection(UUID.randomUUID().toString)
    collection.drop()
    true
  } catch {
    case t: Throwable =>
      println(s"Mongo connection issue: ${t.getMessage}")
      false
  }
}