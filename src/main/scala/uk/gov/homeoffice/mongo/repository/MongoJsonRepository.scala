package uk.gov.homeoffice.mongo.repository

import cats.effect.IO
import cats.implicits._
import com.mongodb.client.model.ReplaceOptions
import com.typesafe.scalalogging.StrictLogging
import org.bson.json._
import org.bson.conversions.Bson
import org.mongodb.scala.bson.Document
import org.mongodb.scala.model.Field
import org.mongodb.scala.model.Filters.or

import io.circe.{Json, JsonObject}
import io.circe.parser._
import scala.util.Try

import com.mongodb.client.result._

import uk.gov.homeoffice.mongo._
import uk.gov.homeoffice.mongo.model._
import uk.gov.homeoffice.mongo.model.syntax._

class MongoJsonRepository(_mongoStreamRepository :MongoStreamRepository) {

  val mongoStreamRepository :MongoStreamRepository = _mongoStreamRepository

  def jsonToDocument(json :Json) :MongoResult[Document] = {
    println(s"CONVERTING $json INTO ${json.deepDropNullValues.spaces4}")
    Try(Document(json.deepDropNullValues.spaces4)).toEither.left.map(exc => MongoError(exc.getMessage()))
  }

  def documentToJson(document :Document) :MongoResult[Json] = {
    val jsonWriterSettings = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build()
    parse(document.toJson(jsonWriterSettings)).left.map(exc => MongoError(exc.getMessage()))
  }

  def resultToJson(result :InsertOneResult) :Json = Json.obj(
    "getInsertedId" -> Json.obj("$oid" -> Json.fromString(result.getInsertedId().asObjectId().getValue().toHexString)),
    "wasAcknowledged" -> Json.fromBoolean(result.wasAcknowledged)
  )

  def resultToJson(result :UpdateResult) :io.circe.Json = {
    import io.circe.syntax._

    println(s"GOT $result")

    val baseJsObj :io.circe.JsonObject = io.circe.JsonObject(
      "getMatchedCount" -> Json.fromLong(result.getMatchedCount()),
      "getModifiedCount" -> Json.fromLong(result.getModifiedCount()),
      "wasAcknowledged" -> Json.fromBoolean(result.wasAcknowledged)
    )

    println(s"Made: $baseJsObj")
    Option(result.getUpsertedId()) match {
      case Some(upsertedId) =>
        val jsWithId = baseJsObj.add("getUpsertedId", Json.obj("$oid" -> Json.fromString(upsertedId.asObjectId().getValue().toHexString())))
        println(s"Id added: $jsWithId")
        jsWithId.asJson
      case None => baseJsObj.asJson
    }

  }

  def insertOne(json :Json) :IO[MongoResult[Json]] = {
    jsonToDocument(json) match {
      case Left(mongoError) => IO(Left(mongoError))
      case Right(document) => mongoStreamRepository.insertOne(document).flatMap {
        case Left(mongoError) => IO(Left(mongoError))
        case Right(insertOneResult) => IO(Right(resultToJson(insertOneResult)))
      }
    }
  }

  def save(json :Json) :IO[MongoResult[Json]] = {
    jsonToDocument(json) match {
      case Left(mongoError) => IO(Left(mongoError))
      case Right(document) => mongoStreamRepository.save(document).flatMap {
        case Left(mongoError) => IO(Left(mongoError))
        case Right(updateOneResult) => IO(Right(resultToJson(updateOneResult)))
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

  def find(json :Json) :JsonObservable = {
    jsonToDocument(json) match {
      case Left(mongoError) => new JsonErrorObservable(mongoError)
      case Right(document) =>
        println(s"EXECUTING JSON: $json")
        new JsonObservableImpl(mongoStreamRepository.find(document), jsonToDocument, documentToJson)
    }
  }

  def all() :fs2.Stream[IO, MongoResult[Json]] = {
    mongoStreamRepository.all().map {
      case Left(mongoError) => Left(mongoError)
      case Right(document) => documentToJson(document)
    }
  }

  def countDocuments(json :Json): IO[MongoResult[Long]] = {
    jsonToDocument(json) match {
      case Left(mongoError) => IO(Left(mongoError))
      case Right(document) => mongoStreamRepository.countDocuments(document)
    }
  }

  def updateOne(target :Json, changes :Json, upsert :Boolean = false) :IO[MongoResult[Json]] = {
    (jsonToDocument(target), jsonToDocument(changes)) match {
      case (Left(exc), _) => IO(Left(exc))
      case (_, Left(exc)) => IO(Left(exc))
      case (Right(t), Right(c)) =>
        mongoStreamRepository.updateOne(t, c, upsert).map {
          case Left(mongoError) => Left(mongoError)
          case Right(updateOneResult) => Right(resultToJson(updateOneResult))
        }
    }
  }

  def updateMany(target :Json, changes :Json, upsert :Boolean = false) :IO[MongoResult[Json]] = {
    (jsonToDocument(target), jsonToDocument(changes)) match {
      case (Left(exc), _) => IO(Left(exc))
      case (_, Left(exc)) => IO(Left(exc))
      case (Right(t), Right(c)) =>
        mongoStreamRepository.updateMany(t, c, upsert).map {
          case Left(mongoError) => Left(mongoError)
          case Right(updateManyResult) => Right(resultToJson(updateManyResult))
        }
    }
  }

  def aggregate(json :List[Json]) :fs2.Stream[IO, MongoResult[Json]] = {
    val jsonInputs :List[MongoResult[Document]] = json.map(jsonToDocument)
    jsonInputs.exists(_.isLeft) match {
      case true =>
        val jsonErrors :List[MongoResult[Json]] = jsonInputs.collect { case Left(mongoError) => Left(mongoError) :MongoResult[Json] }
        fs2.Stream.fromIterator[IO](jsonErrors.iterator, 512)
      case false =>
        val documents = jsonInputs.collect { case Right(document) => document }

        mongoStreamRepository.aggregate(documents).map {
          case Left(mongoError) => Left(mongoError)
          case Right(document) => documentToJson(document)
        }
    }
  }

  def deleteOne(json :Json) :IO[MongoResult[DeleteResult]] = {
    jsonToDocument(json) match {
      case Left(mongoError) => IO(Left(mongoError))
      case Right(document) => mongoStreamRepository.deleteOne(document).flatMap {
        case Left(mongoError) => IO(Left(mongoError))
        case Right(deleteResult) => IO(Right(deleteResult))
      }
    }
  }

  def distinct(fieldName :String, filter :Json) :fs2.Stream[IO, MongoResult[String]] = {
    jsonToDocument(filter) match {
      case Left(mongoError) => fs2.Stream.emit[IO, MongoResult[String]](Left(mongoError))
      case Right(docFilter) =>
        println(s"EXECUTING JSON ($fieldName): $filter")
        mongoStreamRepository.distinct(fieldName, docFilter)
    }
  }
}
