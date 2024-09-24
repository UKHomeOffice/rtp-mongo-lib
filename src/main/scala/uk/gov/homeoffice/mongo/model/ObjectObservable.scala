package uk.gov.homeoffice.mongo.model

import io.circe.Json
import uk.gov.homeoffice.mongo.model.syntax.MongoResult
import cats.effect.IO

sealed trait ObjectObservable[A] {
  def sort(orderBy :Json) :ObjectObservable[A]
  def limit(n :Int) :ObjectObservable[A]
  def skip(n :Int) :ObjectObservable[A]
  def toFS2Stream() :fs2.Stream[IO, MongoResult[A]]
}

class ObjectObservableImpl[A](jsonObservable :JsonObservable, jsonToObject :(Json => MongoResult[A])) extends ObjectObservable[A] {

  def sort(json :Json) :ObjectObservable[A] =
    new ObjectObservableImpl[A](jsonObservable.sort(json), jsonToObject)

  def limit(n :Int) :ObjectObservable[A] = new ObjectObservableImpl[A](jsonObservable.limit(n), jsonToObject)
  def skip(n :Int) :ObjectObservable[A] = new ObjectObservableImpl[A](jsonObservable.skip(n), jsonToObject)

  def toFS2Stream() :fs2.Stream[IO, MongoResult[A]] = jsonObservable.toFS2Stream.map {
    case Left(mongoError) => Left(mongoError)
    case Right(json) => jsonToObject(json)
  }
}
