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
    // See the README for notes about how deepDropNullValues can cause unanticipated errors in your queries (e.g. $group: { _id: null }...)
    Try(Document(json.deepDropNullValues.spaces4)).toEither match {
      case Left(exc) => Left(MongoError(exc.getMessage()))
      case Right(doc) => Right(doc)
    }
  }

  def documentToJson(document :Document) :MongoResult[Json] = {
    val jsonWriterSettings = JsonWriterSettings.builder().outputMode(JsonMode.EXTENDED).build()
    parse(document.toJson(jsonWriterSettings)).left.map(exc => MongoError(exc.getMessage()))
  }

  def resultToJson(result :InsertOneResult) :Json = {
    val id = result.getInsertedId() match {
      case n if !Option(n).isDefined => Json.Null
      case n if n.isNull => Json.Null
      case oid if oid.isObjectId() => Json.fromString(oid.asObjectId().getValue().toHexString)
      case i if i.isNumber => Json.fromInt(i.asNumber().asInt64.intValue)
      case str => Json.fromString(str.asString().getValue())
    }

    Json.obj(
      "getInsertedId" -> id,
      "wasAcknowledged" -> Json.fromBoolean(result.wasAcknowledged)
    )
  }

  def resultToJson(result :UpdateResult) :io.circe.Json = {
    import io.circe.syntax._

    val baseJsObj :io.circe.JsonObject = io.circe.JsonObject(
      "getMatchedCount" -> Json.fromLong(result.getMatchedCount()),
      "getModifiedCount" -> Json.fromLong(result.getModifiedCount()),
      "wasAcknowledged" -> Json.fromBoolean(result.wasAcknowledged)
    )

    result.getUpsertedId() match {
      case n if !Option(n).isDefined => baseJsObj.asJson
      case n if n.isNull => baseJsObj.asJson
      case oid if oid.isObjectId() => baseJsObj.add("getUpsertedId", Json.fromString(oid.asObjectId().getValue().toHexString)).asJson
      case i if i.isNumber => baseJsObj.add("getUpsertedId", Json.fromInt(i.asNumber().asInt64.intValue)).asJson
      case str => baseJsObj.add("getUpsertedId", Json.fromString(str.asString().getValue())).asJson
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
      case Left(mongoError) =>
        throw new Exception(s"mongoJsonRepository.find $mongoError ($json)")
        new JsonErrorObservable(mongoError)
      case Right(document) =>
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

  def deleteMany(json :Json) :IO[MongoResult[DeleteResult]] = {
    jsonToDocument(json) match {
      case Left(mongoError) => IO(Left(mongoError))
      case Right(document) => mongoStreamRepository.deleteMany(document).flatMap {
        case Left(mongoError) => IO(Left(mongoError))
        case Right(deleteResult) => IO(Right(deleteResult))
      }
    }
  }

  def distinct(fieldName :String, filter :Json) :fs2.Stream[IO, MongoResult[String]] = {
    jsonToDocument(filter) match {
      case Left(mongoError) => fs2.Stream.emit[IO, MongoResult[String]](Left(mongoError))
      case Right(docFilter) =>
        mongoStreamRepository.distinct(fieldName, docFilter)
    }
  }
}
