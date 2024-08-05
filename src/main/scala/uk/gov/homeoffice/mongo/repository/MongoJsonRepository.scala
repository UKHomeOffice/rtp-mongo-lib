package uk.gov.homeoffice.mongo

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
import io.circe.parser._
import scala.util.Try

import com.mongodb.client.result._
import uk.gov.homeoffice.mongo.model._

class MongoJsonRepository(_mongoStreamRepository :MongoStreamRepository) {

  val mongoStreamRepository :MongoStreamRepository = _mongoStreamRepository

  def jsonToDocument(json :Json) :MongoResult[Document] = Try(Document(json.spaces4)).toEither.left.map(exc => MongoError(exc.getMessage()))
  def documentToJson(document :Document) :MongoResult[Json] = parse(document.toJson).left.map(exc => MongoError(exc.getMessage()))
  def resultToJson(result :InsertOneResult) :Json = Json.obj(
    "getInsertedId" -> Json.fromString(result.getInsertedId().toString),
    "wasAcknowledged" -> Json.fromBoolean(result.wasAcknowledged)
  )

  def insertOne(json :Json) :IO[MongoResult[Json]] = {
    jsonToDocument(json) match {
      case Left(mongoError) => IO(Left(mongoError))
      case Right(document) => mongoStreamRepository.insertOne(document).flatMap {
        case Left(mongoError) => IO(Left(mongoError))
        case Right(insertOneResult) => IO(Right(resultToJson(insertOneResult)))
      }
    }
  }

  def findOne(json :Json) :IO[MongoResult[Option[Json]]] = {
    jsonToDocument(json) match {
      case Left(mongoError) => IO(Left(mongoError))
      case Right(document) => mongoStreamRepository.findOne(document).flatMap {
        case Left(mongoError) => IO(Left(mongoError))
        case Right(None) => IO(Right(None))
        case Right(Some(document)) => documentToJson(document) match {
          case Left(mongoError) => IO(Left(mongoError))
          case Right(json) => IO(Right(Some(json)))
        }
      }
    }
  }

  def find(json :Json) :fs2.Stream[IO, MongoResult[Json]] = {
    jsonToDocument(json) match {
      case Left(mongoError) => fs2.Stream.emit[IO, MongoResult[Json]](Left(mongoError))
      case Right(document) => mongoStreamRepository.find(document).map {
        case Left(mongoError) => Left(mongoError)
        case Right(document) => documentToJson(document)
      }
    }
  }

  def all() :fs2.Stream[IO, MongoResult[Json]] = {
    mongoStreamRepository.all().map {
      case Left(mongoError) => Left(mongoError)
      case Right(document) => documentToJson(document)
    }
  }

}
