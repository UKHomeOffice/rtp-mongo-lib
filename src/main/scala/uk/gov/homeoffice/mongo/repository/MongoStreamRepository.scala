package uk.gov.homeoffice.mongo.repository

import uk.gov.homeoffice.mongo._
import uk.gov.homeoffice.mongo.model._
import uk.gov.homeoffice.mongo.model.syntax._
import com.mongodb.client.result._

import cats.effect.IO
import cats.implicits._
import com.mongodb.client.model.{UpdateOptions, ReplaceOptions}
import com.typesafe.scalalogging.StrictLogging
import org.bson.json._
import org.bson.conversions.Bson
import org.mongodb.scala.bson.Document
import org.mongodb.scala.model.Field
import org.mongodb.scala.model.Filters.or
import org.mongodb.scala.bson._
import scala.concurrent.ExecutionContext

import org.bson.types.ObjectId

class MongoStreamRepository(
  val mongoConnection :MongoConnection,
  val collectionName :String,
  val primaryKeys :List[String] = List()
) extends StrictLogging {
  import uk.gov.homeoffice.mongo.MongoHelpers._
  implicit val ec :ExecutionContext = ExecutionContext.global

  val collection = mongoConnection.mongoCollection[Document](collectionName)

  def insertOne(document :Document) :IO[MongoResult[InsertOneResult]] = {
    val result = collection.insertOne(document)

    futureToIOMongoResult(result.head())
  }

  def save(document :Document) :IO[MongoResult[UpdateResult]] = {
    import scala.jdk.CollectionConverters._
    
    val elems :java.util.List[BsonElement] = primaryKeys
      .map { pk => (pk, document.get[BsonValue](pk)) }
      .filter(_._2.isDefined)
      .map { case (pk, bsonValue) => BsonElement(pk, bsonValue.get) }
      .toList
      .asJava

    val searchDoc = new BsonDocument(elems)
    val replaceOptions = new ReplaceOptions().upsert(true)

    futureToIOMongoResult { collection.replaceOne(
      searchDoc,
      document,
      replaceOptions
    ).toSingle().toFuture() }
  }

  def findOne(query :Document) :IO[MongoResult[Option[Document]]] =
    futureToIOMongoResult(collection.find(query).toSingle().toFutureOption())

  def find(query :Document) :fs2.Stream[IO, MongoResult[Document]] = {
    val qry = collection.find(query)
    fromObservable(qry)
  }

  def all() :fs2.Stream[IO, MongoResult[Document]] = {
    val qry = collection.find()
    fromObservable(qry)
  }

  def countDocuments(query :Document) :IO[MongoResult[Long]] =
    futureToIOMongoResult(collection.countDocuments(query).toFuture())

  def updateOne(target :Document, changes :Document, upsert :Boolean = false) :IO[MongoResult[UpdateResult]] = {
    val result = collection.updateOne(target, changes, new UpdateOptions().upsert(upsert))
    futureToIOMongoResult(result.head())
  }

  def updateMany(target :Document, changes :Document, upsert :Boolean = false) :IO[MongoResult[UpdateResult]] = {
    val result = collection.updateMany(target, changes, new UpdateOptions().upsert(upsert))
    futureToIOMongoResult(result.head())
  }

  def aggregate(query :List[Document]) :fs2.Stream[IO, MongoResult[Document]] = {
    val qry = collection.aggregate(query)
    fromObservable(qry)
  }

  def deleteOne(query :Document) :IO[MongoResult[DeleteResult]] = {
    val result = collection.deleteOne(query)
    futureToIOMongoResult(result.toSingle().toFuture())
  }

  def drop() :IO[MongoResult[Unit]] = {
    futureToIOMongoResult(collection.drop().toFuture())
  }

  def distinct(fieldName :String, query :Document) :fs2.Stream[IO, MongoResult[String]] = {
    val qry = collection.distinct[String](fieldName, query)
    fromDirectObservableString(qry)
  }
}
