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

abstract class MongoCasbahSalatRepository[A](_mongoCasbahRepository :MongoCasbahRepository) {

  val mongoCasbahRepository :MongoCasbahRepository = _mongoCasbahRepository

  def toMongoDBObject(a :A) :MongoResult[MongoDBObject]
  def fromMongoDBObject(mongoDBObject :MongoDBObject) :MongoResult[A]

  def insert(a :A) :A = {
    toMongoDBObject(a) match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: $mongoError")
      case Right(mongoDBObject) =>
        mongoCasbahRepository.insertOne(mongoDBObject)
        a
    }
  }

  def save(a :A) :A = {
    toMongoDBObject(a) match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: $mongoError")
      case Right(mongoDBObject) =>
        mongoCasbahRepository.save(mongoDBObject)
        a
    }
  }

  def findOne(q :MongoDBObject) :Option[A] = {
    mongoCasbahRepository.findOne(q).map { mongoDBObject =>
      fromMongoDBObject(mongoDBObject) match {
        case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: $mongoError")
        case Right(a) => a
      }
    }
  }

  def find(q :MongoDBObject) :DBCursor[A] = mongoCasbahRepository.find(q).map { fromMongoDBObject(_) match {
    case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: $mongoError")
    case Right(a) => a
  }}
  def find(q :MongoDBObject, p :MongoDBObject) :DBCursor[A] = mongoCasbahRepository.find(q, p).map { fromMongoDBObject(_) match {
    case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: $mongoError")
    case Right(a) => a
  }}
  def aggregate(q :List[MongoDBObject]) :List[MongoDBObject] = mongoCasbahRepository.aggregate(q)
  def count(q :MongoDBObject) :Long = mongoCasbahRepository.count(q)

  def update(q :MongoDBObject, o :MongoDBObject, multi :Boolean = true, upsert :Boolean = false) :CasbahWriteResult =
    mongoCasbahRepository.update(q, o, multi, upsert)

  def drop() :Unit = mongoCasbahRepository.drop()
  def remove(query :MongoDBObject) :CasbahDeleteResult = mongoCasbahRepository.remove(query)
  def distinct(fieldName :String, q :MongoDBObject) :List[String] = mongoCasbahRepository.distinct(fieldName, q)

}
