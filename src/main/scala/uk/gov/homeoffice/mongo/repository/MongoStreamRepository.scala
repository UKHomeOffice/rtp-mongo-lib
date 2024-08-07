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
import org.mongodb.scala.bson._


class MongoStreamRepository(
  val mongoConnection :MongoConnection,
  val collectionName :String,
  val primaryKeys :List[String] = List()
) {

  val collection = mongoConnection.mongoCollection[Document](collectionName)

  def insertOne(document :Document) :IO[MongoResult[InsertOneResult]] = {
    val result = collection.insertOne(document)
    IO.fromFuture(IO(result.head())).map { insertOneResult => Right(insertOneResult) }
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

    IO.fromFuture(IO(collection.replaceOne(
      searchDoc,
      document,
      replaceOptions
    ).toSingle().toFuture())).map { updateResult => Right(updateResult) }
  }

  def findOne(query :Document) :IO[MongoResult[Option[Document]]] = {
    IO.fromFuture(IO(collection.find(query).toSingle().toFutureOption())).map { maybeDocument => Right(maybeDocument) }
  }

  def find(query :Document) :fs2.Stream[IO, MongoResult[Document]] = {
    val qry = collection.find(query)
    MongoHelpers.fromObservable(qry)
  }

  def all() :fs2.Stream[IO, MongoResult[Document]] = {
    val qry = collection.find()
    MongoHelpers.fromObservable(qry)
  }

  def aggregate(query :List[Document]) :fs2.Stream[IO, MongoResult[Document]] = {
    val qry = collection.aggregate(query)
    MongoHelpers.fromObservable(qry)
  }

}
