package uk.gov.homeoffice.mongo.repository

import uk.gov.homeoffice.mongo._
import uk.gov.homeoffice.mongo.model._
import uk.gov.homeoffice.mongo.casbah._

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

// TODO: change as[A].toOption.get into something better, that wont throw Generic NotFound expections etc.
case class CasbahInsertResult(result :Json) {
  def getInsertedId() :ObjectId = new ObjectId(result.hcursor.downField("getInsertedId").as[String].toOption.get)
  def wasAcknowledged() :Boolean = result.hcursor.downField("wasAcknowledged").as[Boolean].toOption.get
}

case class CasbahWriteResult(result :Json) {
  def getUpsertedId() :ObjectId = new ObjectId(result.hcursor.downField("getUpsertedId").as[String].toOption.get)
  def wasAcknowledged() :Boolean = result.hcursor.downField("wasAcknowledged").as[Boolean].toOption.get
  def getMatchedCount() :Int = result.hcursor.downField("getMatchedCount").as[Int].toOption.get
  def getModifiedCount() :Int = result.hcursor.downField("getModifiedCount").as[Int].toOption.get
  def getN() :Int = result.hcursor.downField("getModifiedCount").as[Int].toOption.get
}

case class CasbahDeleteResult(result :Json) {
  def getN() :Int = result.hcursor.downField("getN").as[Int].toOption.get
}

class MongoCasbahSalatRepository[A](_mongoObjectRepository :MongoObjectRepository[A]) {
  import cats.effect.unsafe.implicits.global

  val mongoObjectRepository :MongoObjectRepository[A] = _mongoObjectRepository

  def insert(a :A) :A = {
    mongoObjectRepository.insertOne(a).unsafeRunSync() match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: $mongoError")
      case Right(_) => a
    }
  }

  def save(a :A) :A = {
    mongoObjectRepository.save(a).unsafeRunSync() match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: $mongoError")
      case Right(a) => a
    }
  }

  def findOne(filter :MongoDBObject) :Option[A] = {
    mongoObjectRepository.findOne(filter.toJson).unsafeRunSync() match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: $mongoError")
      case Right(None) => None
      case Right(Some(obj)) => Some(obj.asInstanceOf[A])
    }
  }

  def find(filter :MongoDBObject) :DBCursor = {
    def stripErrors(in :MongoResult[Json]) = MongoDBObject(in.right.get)
    val resultList :List[MongoDBObject] = mongoObjectRepository.mongoJsonRepository.find(filter.toJson).map(a => stripErrors(a)).compile.toList.unsafeRunSync()
    new DBCursor(resultList)
  }

  def find(filter :MongoDBObject, projection :MongoDBObject) :DBCursor = {
    def stripErrors(in :MongoResult[Json]) = MongoDBObject(in.right.get)
    val resultList :List[MongoDBObject] = mongoObjectRepository.mongoJsonRepository.find(filter.toJson).map(a => stripErrors(a)).compile.toList.unsafeRunSync()
    new DBCursor(resultList)
  }

  def aggregate(filter :List[MongoDBObject]) :List[MongoDBObject] = {
    val jsonList = filter.map(_.toJson)
    val streamResponse :fs2.Stream[IO, MongoResult[Json]] = mongoObjectRepository.mongoJsonRepository.aggregate(jsonList)
    streamResponse.compile.toList.unsafeRunSync().collect { case Right(doc) => MongoDBObject.apply(doc) }
  }

  /* untested below here */

  def update(target :MongoDBObject, changes :MongoDBObject) :CasbahWriteResult = {
    mongoObjectRepository.mongoJsonRepository.updateMany(target.toJson, changes.toJson).unsafeRunSync() match {
      case Left(mongoError) => throw new Exception(s"MONGO EXCEPTION: $mongoError")
      case Right(updateResultJson) => CasbahWriteResult(updateResultJson)
    }
  }

  def drop() :Unit = IO.fromFuture(IO(
    mongoObjectRepository
    .mongoJsonRepository
    .mongoStreamRepository
    .collection
    .drop()
    .toFuture()
  )).unsafeRunSync()

  def remove(query :MongoDBObject) :CasbahDeleteResult =
    mongoObjectRepository.mongoJsonRepository.deleteOne(query.toJson).unsafeRunSync() match {
      case Left(mongoError) => throw new MongoException(mongoError.message)
      /* TODO: make mongoJsonRepository jsonify the delete result for consistency */
      case Right(deleteResult) => CasbahDeleteResult(Json.obj("getN" -> Json.fromLong(deleteResult.getDeletedCount)))
    }
}

