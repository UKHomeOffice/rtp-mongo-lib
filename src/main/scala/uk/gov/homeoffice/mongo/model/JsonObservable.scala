package uk.gov.homeoffice.mongo.model

import io.circe.{Json, JsonObject}
import org.mongodb.scala.bson.Document
import uk.gov.homeoffice.mongo.model.syntax.MongoResult
import cats.effect.IO

sealed trait JsonObservable {
  def projection(projection :Json) :JsonObservable
  def sort(document :Json) :JsonObservable
  def limit(n :Int) :JsonObservable
  def skip(n :Int) :JsonObservable

  def toFS2Stream() :fs2.Stream[IO, MongoResult[Json]]
}

class JsonObservableImpl(streamObservable :StreamObservable, jsonToDocument :Json => MongoResult[Document], documentToJson :Document => MongoResult[Json]) extends JsonObservable {

  def projection(projection :Json) :JsonObservable = {
    jsonToDocument(projection) match {
      case Left(mongoError) => new JsonErrorObservable(mongoError)
      case Right(document) => new JsonObservableImpl(streamObservable.projection(document), jsonToDocument, documentToJson)
    }
  }

  /*
   *
   * In Mongo Json land the following two objects are comparable:
   *   { "application.isDeleted" -> true }
   *
   *   and
   *
   *   { "application" -> { "isDeleted" -> true }}
   *
   *  writing queries using the dotted notation is natural for Mongo.
   *
   *  When writing MongoDBObject, I automatically "inflate" objects to the second internal representation. This allows me to
  */

  def sort(json :Json) :JsonObservable = {
    jsonToDocument(json) match {
      case Left(mongoError) => new JsonErrorObservable(mongoError)
      case Right(document) => new JsonObservableImpl(streamObservable.sort(document), jsonToDocument, documentToJson)
    }
  }

  def limit(n :Int) :JsonObservable = new JsonObservableImpl(streamObservable.limit(n), jsonToDocument, documentToJson)
  def skip(n :Int) :JsonObservable = new JsonObservableImpl(streamObservable.skip(n), jsonToDocument, documentToJson)

  def toFS2Stream() :fs2.Stream[IO, MongoResult[Json]] = streamObservable.toFS2Stream().map {
    case Left(mongoError) => Left(mongoError)
    case Right(document) => documentToJson(document)
  }
}

class JsonErrorObservable(mongoError :MongoError) extends JsonObservable {
  def projection(projection :Json) :JsonObservable = this
  def sort(document :Json) :JsonObservable = this
  def limit(n :Int) :JsonObservable = this
  def skip(n :Int) :JsonObservable = this
  def toFS2Stream() :fs2.Stream[IO, MongoResult[Json]] = {
    fs2.Stream.emit[IO, MongoResult[Json]](Left(mongoError))
  }
}

