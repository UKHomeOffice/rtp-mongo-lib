package uk.gov.homeoffice.mongo.repository

import uk.gov.homeoffice.mongo._
import uk.gov.homeoffice.mongo.model._
import uk.gov.homeoffice.mongo.model.syntax._
import com.mongodb.client.result._

import cats.effect.IO
import cats.implicits._
import com.mongodb.client.model.{UpdateOptions, ReplaceOptions}
import org.bson.json._
import org.bson.conversions.Bson
import org.mongodb.scala.bson.Document
import org.mongodb.scala.model.Field
import org.mongodb.scala.model.Filters.or
import org.mongodb.scala.bson._
import scala.concurrent.ExecutionContext

import org.bson.types.ObjectId

/* Primary Keys is something of a non-sensical argument. In order to test rtp-mongo-lib I ported a huge library to use this
 * code. Only then did I cement the behaviour that worked and added tests. When I came to add the tests, only then did I realise
 * that mongo doesn't support separate compound primary keys. Well, it supports compound keys, by virtue of allowing things to be
 * nesting inside _id.. e.g.   { _id: { key1 : 123, key2: 543 }}.
 *
 * As a result, the only two values the make sense to pass are
 *  List("_id"), which covers most cases
 *  List.empty when you want all .save(a) calls to be treated as insertOne(a) calls instead.
*/

class MongoStreamRepository(
  val mongoConnection :MongoConnection,
  val collectionName :String,
  val primaryKeys :List[String] = List()
) {
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

    /*
     * Important: If elems ends up being empty because the document
     * you're saving doesn't have any primary key fields, replaceOne
     * searches on {} meaning it replaces _any_ value (aka the first db
     * row it counters!)
     *
     * Hence, if this happens we switch to insertOne here.
    */
    (elems.isEmpty) match {
      case true =>
        insertOne(document).map {
          case Left(mongoError) => Left(mongoError)
          /* convert insertResult to updateResult */
          case Right(insertResult) if insertResult.wasAcknowledged =>
           Right(UpdateResult.acknowledged(0, 1, insertResult.getInsertedId()))
          case Right(insertResult) =>
           Right(UpdateResult.unacknowledged())
        }
      case false =>
        val searchDoc = new BsonDocument(elems)
        val replaceOptions = new ReplaceOptions().upsert(true)

        futureToIOMongoResult { collection.replaceOne(
          searchDoc,
          document,
          replaceOptions
        ).toSingle().toFuture() }
    }
  }

  def findOne(query :Document) :IO[MongoResult[Option[Document]]] =
    futureToIOMongoResult(collection.find(query).toSingle().toFutureOption())

  def find(query :Document) :StreamObservable = {
    val qry = collection.find(query)
    new StreamObservable(qry)
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

  def deleteMany(query :Document) :IO[MongoResult[DeleteResult]] = {
    val result = collection.deleteMany(query)
    futureToIOMongoResult(result.toSingle().toFuture())
  }

  def drop() :IO[MongoResult[Unit]] = {
    futureToIOMongoResult(collection.drop().toFuture())
  }

  def distinct(fieldName :String, query :Document) :fs2.Stream[IO, MongoResult[String]] = {
    val qry = collection.distinct[String](fieldName, query)
    fromObservable(qry)
  }
}
