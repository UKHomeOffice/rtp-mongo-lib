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
      case Left(mongoError) => throw new Exception(s"MONGO EXCEPTION: $mongoError")
      case Right(insertResultJson) => CasbahInsertResult(insertResultJson)
    }
  }

  def save(mongoDBObject :MongoDBObject) :CasbahWriteResult = {
    mongoJsonRepository.save(mongoDBObject.toJson).unsafeRunSync() match {
      case Left(mongoError) => throw new Exception(s"MONGO EXCEPTION: $mongoError")
      case Right(writeResultJson) => CasbahWriteResult(writeResultJson)
    }
  }

  def findOne(filter :MongoDBObject) :Option[MongoDBObject] = {
    mongoJsonRepository.findOne(filter.toJson).unsafeRunSync() match {
      case Left(mongoError) => throw new Exception(s"MONGO EXCEPTION: $mongoError")
      case Right(None) => None
      case Right(Some(json)) => Some(MongoDBObject(json))
    }
  }

  def find(filter :MongoDBObject) :DBCursor = {
    def stripErrors(in :MongoResult[Json]) = in match {
      case Left(appError) => throw new Exception(s"MONGO EXCEPTION: $appError")
      case Right(json) => MongoDBObject(json)
    }
    val resultList :List[MongoDBObject] = mongoJsonRepository.find(filter.toJson).map(a => stripErrors(a)).compile.toList.unsafeRunSync()
    new DBCursor(resultList)
  }

  def find(filter :MongoDBObject, projection :MongoDBObject) :DBCursor = {
    def stripErrors(in :MongoResult[Json]) = in match {
      case Left(appError) => throw new Exception(s"MONGO EXCEPTION: $appError")
      case Right(json) => MongoDBObject(json)
    }
    val resultList :List[MongoDBObject] = mongoJsonRepository.find(filter.toJson).map(a => stripErrors(a)).compile.toList.unsafeRunSync()
    new DBCursor(resultList)
  }

  def aggregate(filter :List[MongoDBObject]) :List[MongoDBObject] = {
    val jsonList = filter.map(_.toJson)
    val streamResponse :fs2.Stream[IO, MongoResult[Json]] = mongoJsonRepository.aggregate(jsonList)
    streamResponse.compile.toList.unsafeRunSync().collect { case Right(doc) => MongoDBObject.apply(doc) }
  }

  def update(target :MongoDBObject, changes :MongoDBObject) :CasbahWriteResult = {
    mongoJsonRepository.updateMany(target.toJson, changes.toJson).unsafeRunSync() match {
      case Left(mongoError) => throw new Exception(s"MONGO EXCEPTION: $mongoError")
      case Right(updateResultJson) => CasbahWriteResult(updateResultJson)
    }
  }

  def drop() :Unit = IO.fromFuture(IO(
    mongoJsonRepository
    .mongoStreamRepository
    .collection
    .drop()
    .toFuture()
  )).unsafeRunSync()

  def remove(query :MongoDBObject) :CasbahDeleteResult =
    mongoJsonRepository.deleteOne(query.toJson).unsafeRunSync() match {
      case Left(mongoError) => throw new MongoException(mongoError.message)
      /* TODO: make mongoJsonRepository jsonify the delete result for consistency */
      case Right(deleteResult) => CasbahDeleteResult(Json.obj("getN" -> Json.fromLong(deleteResult.getDeletedCount)))
    }
}
