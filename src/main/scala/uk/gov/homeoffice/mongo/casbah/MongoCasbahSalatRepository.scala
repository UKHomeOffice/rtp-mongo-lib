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

  def toMongoObject(a :A) :MongoResult[MongoDBObject]
  def fromMongoObject(mongoDBObject :MongoDBObject) :MongoResult[A]

  def insert(a :A) :A = {
    toMongoObject(a) match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: $mongoError")
      case Right(mongoDBObject) =>
        mongoCasbahRepository.insertOne(mongoDBObject)
        a
    }
  }

  def save(a :A) :A = {
    toMongoObject(a) match {
      case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: $mongoError")
      case Right(mongoDBObject) =>
        mongoCasbahRepository.save(mongoDBObject)
        a
    }
  }

  def findOne(filter :MongoDBObject) :Option[A] = {
    mongoCasbahRepository.findOne(filter).map { mongoDBObject =>
      fromMongoObject(mongoDBObject) match {
        case Left(mongoError) => throw new MongoException(s"MONGO EXCEPTION: $mongoError")
        case Right(a) => a
      }
    }
  }

  def find(filter :MongoDBObject) :DBCursor = mongoCasbahRepository.find(filter)
  def find(filter :MongoDBObject, projection :MongoDBObject) :DBCursor = mongoCasbahRepository.find(filter, projection)
  def aggregate(filter :List[MongoDBObject]) :List[MongoDBObject] = mongoCasbahRepository.aggregate(filter)
  def update(target :MongoDBObject, changes :MongoDBObject) :CasbahWriteResult = mongoCasbahRepository.update(target, changes)
  def drop() :Unit = mongoCasbahRepository.drop()
  def remove(query :MongoDBObject) :CasbahDeleteResult = mongoCasbahRepository.remove(query)

}
