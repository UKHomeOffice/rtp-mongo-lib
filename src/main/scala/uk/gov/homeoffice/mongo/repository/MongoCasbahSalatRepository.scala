package uk.gov.homeoffice.mongo

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

class MongoException(msg :String) extends Exception(msg)

class MongoCasbahSalatRepository[A](_mongoObjectRepository :MongoObjectRepository[A]) {
  import cats.effect.unsafe.implicits.global

  val mongoObjectRepository :MongoObjectRepository[A] = _mongoObjectRepository

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

  def find(filter :MongoDBObject) :List[A] = {
    def stripErrors(in :MongoResult[A]) = in.right.get
    mongoObjectRepository.find(filter.toJson).map(a => stripErrors(a)).compile.toList.unsafeRunSync()
  }

  def aggregate(filter :List[MongoDBObject]) :List[MongoDBObject] = {
    val jsonList = filter.map(_.toJson)
    val streamResponse :fs2.Stream[IO, MongoResult[Json]] = mongoObjectRepository.mongoJsonRepository.aggregate(jsonList)
    streamResponse.compile.toList.unsafeRunSync().collect { case Right(doc) => MongoDBObject.apply(doc) }
  }

}

