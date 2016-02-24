package uk.gov.homeoffice.mongo

import java.util.UUID
import com.mongodb.casbah.MongoClientURI
import com.mongodb.casbah.commons.MongoDBObject
import uk.gov.homeoffice.mongo.casbah.Mongo
import uk.gov.homeoffice.mongo.salat.Repository

object ExampleApp extends App {
  val repository = new ExampleRepository with ExampleMongo
  val example = Example(description = s"Save test! Random ID of ${UUID.randomUUID()}")

  repository save example

  repository.findOne(MongoDBObject("description" -> example.description)) match {
    case Some(ex) => println("Successfully saved to database and read back in!")
    case None => println("An issue occurred while trying to save to database and read back in!!!!!")
  }
}

trait ExampleMongo extends Mongo {
  lazy val db = ExampleMongo.exampledb
}

object ExampleMongo {
  lazy val exampledb = Mongo.db(MongoClientURI("mongodb://localhost:27017/rtp-example"))
}

trait ExampleRepository extends Repository[Example] {
  val collectionName = "examples"
}

case class Example(description: String)