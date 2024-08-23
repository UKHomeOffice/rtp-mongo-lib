package uk.gov.homeoffice.mongo.casbah

import uk.gov.homeoffice.mongo._
import uk.gov.homeoffice.mongo.model._
import uk.gov.homeoffice.mongo.repository._
import uk.gov.homeoffice.mongo.model.syntax._

import cats.effect.IO
import cats.implicits._
import com.mongodb.client.model.ReplaceOptions
import com.typesafe.scalalogging.StrictLogging
import org.bson.json._
import org.bson.conversions.Bson
import org.mongodb.scala.bson.Document
import org.mongodb.scala.model.Field
import org.mongodb.scala.model.Filters.or

import io.circe.Json
import scala.util.Try
import org.bson.types.ObjectId

class MongoCasbahRepository(_mongoJsonRepository :MongoJsonRepository) {
  import cats.effect.unsafe.implicits.global

  val mongoJsonRepository :MongoJsonRepository = _mongoJsonRepository

  def insertOne(mongoDBObject :MongoDBObject) :CasbahInsertResult = {
    mongoJsonRepository.insertOne(mongoDBObject.toJson).unsafeRunSync() match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION MongoCasbahRepository.insertOne($mongoDBObject): $mongoError")
      case Right(insertResultJson) => CasbahInsertResult(insertResultJson)
    }
  }

  def save(mongoDBObject :MongoDBObject) :CasbahWriteResult = {
    mongoJsonRepository.save(mongoDBObject.toJson).unsafeRunSync() match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: MongoCasbahRepository.save($mongoDBObject): $mongoError")
      case Right(writeResultJson) => CasbahWriteResult(writeResultJson)
    }
  }

  def findOne(filter :MongoDBObject) :Option[MongoDBObject] = {
    mongoJsonRepository.findOne(filter.toJson).unsafeRunSync() match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: MongoCasbahRepository.findOne($filter): $mongoError")
      case Right(None) => None
      case Right(Some(json)) => Some(MongoDBObject(json))
    }
  }

  def find(filter :MongoDBObject) :DBCursor[MongoDBObject] = {
    def stripErrors(in :MongoResult[Json]) = in match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: MongoCasbahRepository.find($filter): $mongoError")
      case Right(json) => MongoDBObject(json)
    }
    val resultList :List[MongoDBObject] = mongoJsonRepository.find(filter.toJson).map(a => stripErrors(a)).compile.toList.unsafeRunSync()
    new DBCursor(resultList)
  }

  def find(filter :MongoDBObject, projection :MongoDBObject) :DBCursor[MongoDBObject] = {
    def stripErrors(in :MongoResult[Json]) = in match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: MongoCasbahRepository.find($filter, $projection): $mongoError")
      case Right(json) => MongoDBObject(json)
    }
    val resultList :List[MongoDBObject] = mongoJsonRepository.find(filter.toJson).map(a => stripErrors(a)).compile.toList.unsafeRunSync()
    new DBCursor(resultList)
  }

  def count(filter :MongoDBObject) :Long = {
    mongoJsonRepository.countDocuments(filter.toJson).unsafeRunSync() match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: MongoCasbahRepository.count($filter): $mongoError")
      case Right(countLong) => countLong
    }
  }

  def aggregate(filter :List[MongoDBObject]) :List[MongoDBObject] = {
    val jsonList = filter.map(_.toJson)
    val streamResponse :fs2.Stream[IO, MongoResult[Json]] = mongoJsonRepository.aggregate(jsonList)
    val resultList = streamResponse.compile.toList.unsafeRunSync()
    MongoHelpers.mongoResultCollect(resultList) match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: MongoCasbahRepository.aggregate($filter): $mongoError")
      case Right(listOfResults) => listOfResults.map(MongoDBObject.apply)
    }
  }

  def update(target :MongoDBObject, changes :MongoDBObject, multi: Boolean = true, upsert :Boolean = false) :CasbahWriteResult = {
    multi match {
      case true =>
        mongoJsonRepository.updateMany(target.toJson, changes.toJson, upsert).unsafeRunSync() match {
          case Left(mongoError) => throw new Exception(s"MONGO EXCEPTION: MongoCasbahRepository.update($target, $changes): (UpdateMany) $mongoError")
          case Right(updateResultJson) => CasbahWriteResult(updateResultJson)
        }
      case false =>
        mongoJsonRepository.updateOne(target.toJson, changes.toJson, upsert).unsafeRunSync() match {
          case Left(mongoError) => throw new Exception(s"MONGO EXCEPTION: MongoCasbahRepository.update($target, $changes): (UpdateOne) $mongoError")
          case Right(updateResultJson) => CasbahWriteResult(updateResultJson)
        }
    }
  }

  def drop() :Unit =
    mongoJsonRepository
    .mongoStreamRepository
    .drop()
    .unsafeRunSync()

  def remove(query :MongoDBObject) :CasbahDeleteResult =
    mongoJsonRepository.deleteOne(query.toJson).unsafeRunSync() match {
      case Left(mongoError) => throw new MongoException(mongoError.message)
      /* TODO: make mongoJsonRepository jsonify the delete result for consistency */
      case Right(deleteResult) => CasbahDeleteResult(Json.obj("getN" -> Json.fromLong(deleteResult.getDeletedCount)))
    }

  def ensureUniqueIndex(c :org.mongodb.scala.MongoCollection[Document], indexName :String, fieldNames :List[String]) :Unit = {
    println(s"Ensuring Unique Index $indexName")
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    import org.mongodb.scala.bson._
    def isEssentialIndex(d :Document) :Boolean = d.get("name").map(_ == BsonString(indexName)).getOrElse(false)

    val indexOptions = new org.mongodb.scala.model.IndexOptions()
    indexOptions.name(indexName)
    indexOptions.unique(true)

    val indexDoc = org.mongodb.scala.bson.BsonDocument()
    fieldNames.foreach { f => indexDoc.put(f, org.mongodb.scala.bson.BsonInt64(1)) }

    val result :scala.concurrent.Future[Either[String, String]] = c.createIndex(
      indexDoc,
      indexOptions
    ).toFuture()
      .map { _ => Right(s"$indexName added to collection") }
      .recoverWith {
        case exc =>
          c.listIndexes().toFuture().map { indexList => indexList.exists(isEssentialIndex) match {
            case true => Right(s"Mongo $indexName already present")
            case false => Left(s"The Mongo Database is missing a critically important index named $indexName. This prevents corruption")
          }}
      }

    MongoHelpers.futureToIOMongoResult(result).unsafeRunSync() match {
      case Left(mongoError) => throw new MongoException(mongoError.message)
      case Right(message) => println(s"Adding unique index result: $message")
    }
  }
}
