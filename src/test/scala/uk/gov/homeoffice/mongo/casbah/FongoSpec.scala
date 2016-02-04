package uk.gov.homeoffice.mongo.casbah

import org.specs2.mutable.Specification
import com.github.fakemongo.Fongo
import com.mongodb.casbah.MongoDB

/**
 * Mix in this trait to provide a connection to an embedded Mongo for testing.
 * The current implementation uses Fongo - note that this does not implement all of the Mongo API.
 */
@deprecated(message = "Instead use EmbeddedMongoSpec", since = "04-02-2016")
trait FongoSpec extends MongoSpec {
  this: Specification =>

  isolated

  val fongo = new Fongo(mongoClientURI.getURI)

  override lazy val mongodb = new MongoDB(fongo.getDB(database))
}