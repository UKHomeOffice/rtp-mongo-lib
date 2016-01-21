package uk.gov.homeoffice.mongo.casbah

import java.util.UUID
import scala.util.Try
import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Specification
import org.specs2.specification.AroundEach
import com.mongodb.casbah.{MongoClient, MongoClientURI, MongoDB}

trait MongoSpec extends AroundEach {
  this: Specification =>

  isolated

  val server = "127.0.0.1"

  val port = 27017

  val database = s"test-${UUID.randomUUID()}"

  val mongoConnectionUri = s"mongodb://$server:$port/$database"

  lazy val mongoClientURI = MongoClientURI(mongoConnectionUri)

  lazy val mongoClient = MongoClient(mongoClientURI)

  lazy val mongodb = mongoClient(database)

  /** ONLY here for compatibility with the old way of interacting with Mongo where WithMongo would have been used. */
  /*lazy val mongoConnector = new MongoConnector(mongoClientURI.getURI) {
    override lazy val db = mongodb
  }*/

  override def around[T: AsResult](t: => T): Result = try {
    debug(s"+ Created $database in spec $getClass")
    AsResult(t)
  } finally {
    debug(s"x Dropping $database")
    closeMongo
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
    lazy val db: MongoDB = mongodb
  }
}