package uk.gov.homeoffice.mongo

import uk.gov.homeoffice.mongo.model._
import com.mongodb.client.result._

import cats.effect.IO
import cats.implicits._
import com.mongodb.client.model.ReplaceOptions
import com.typesafe.scalalogging.StrictLogging
import org.bson.json._
import org.bson.conversions.Bson
import org.mongodb.scala.bson.Document
import org.mongodb.scala.model.Field
import org.mongodb.scala.model.Filters.or

class MongoStreamRepository(mongoConnection :MongoConnection, collectionName :String, primaryKeys :List[String] = List()) {

  val collection = mongoConnection.mongoCollection[Document](collectionName)

  def insertOne(document :Document) :IO[MongoResult[InsertOneResult]] = {
    val result = collection.insertOne(document)
    IO.fromFuture(IO(result.head())).map { insertOneResult => Right(insertOneResult) }
  }

  def findOne(query :Document) :IO[MongoResult[Option[Document]]] = {
    IO.fromFuture(IO(collection.find(query).head())).map { document => Right(Some(document)) }
  }

  def find(query :Document) :fs2.Stream[IO, MongoResult[Document]] = {
    val qry = collection.find(query)
    MongoHelpers.fromObservable(qry)
  }

  def all() :fs2.Stream[IO, MongoResult[Document]] = {
    val qry = collection.find()
    MongoHelpers.fromObservable(qry)
  }

}
