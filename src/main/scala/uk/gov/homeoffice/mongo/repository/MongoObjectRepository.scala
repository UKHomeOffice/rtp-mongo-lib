package uk.gov.homeoffice.mongo.repository

import uk.gov.homeoffice.mongo._
import uk.gov.homeoffice.mongo.model._

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

abstract class MongoObjectRepository[A](_mongoJsonRepository :MongoJsonRepository) {

  val mongoJsonRepository :MongoJsonRepository = _mongoJsonRepository

  def jsonToObject(json :Json) :MongoResult[A]
  def objectToJson(obj :A) :MongoResult[Json]

  def insertOne(a :A) :IO[MongoResult[Json]] = {
    objectToJson(a) match {
      case Left(mongoError) => IO(Left(mongoError))
      case Right(json) => mongoJsonRepository.insertOne(json)
    }
  }

  def save(a :A) :IO[MongoResult[A]] = {
    objectToJson(a) match {
      case Left(mongoError) => IO(Left(mongoError))
      case Right(json) => mongoJsonRepository.save(json).map {
        case Left(mongoError) => Left(mongoError)
        case Right(json) => Right(a)
      }
    }
  }

  def findOne(filter :Json) :IO[MongoResult[Option[A]]] = {
    mongoJsonRepository.findOne(filter).flatMap {
      case Left(mongoError) => IO(Left(mongoError))
      case Right(None) => IO(Right(None))
      case Right(Some(json)) => jsonToObject(json) match {
        case Left(mongoError) => IO(Left(mongoError))
        case Right(a) => IO(Right(Some(a)))
      }
    }
  }

  def find(filter :Json) :fs2.Stream[IO, MongoResult[A]] = {
    mongoJsonRepository.find(filter).map {
      case Left(mongoError) => Left(mongoError)
      case Right(json) => jsonToObject(json) match {
        case Left(mongoError) => Left(mongoError)
        case Right(a) => Right(a)
      }
    }
  }

  def all() :fs2.Stream[IO, MongoResult[A]] = {
    mongoJsonRepository.all().map {
      case Left(mongoError) => Left(mongoError)
      case Right(json) => jsonToObject(json) match {
        case Left(mongoError) => Left(mongoError)
        case Right(a) => Right(a)
      }
    }
  }
}

