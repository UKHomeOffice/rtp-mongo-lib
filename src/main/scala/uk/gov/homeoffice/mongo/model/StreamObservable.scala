package uk.gov.homeoffice.mongo.model

import org.mongodb.scala.FindObservable
import org.mongodb.scala.bson.Document
import uk.gov.homeoffice.mongo.model.syntax.MongoResult
import cats.effect.IO

class StreamObservable(findObservable :FindObservable[Document]) {
  import uk.gov.homeoffice.mongo.MongoHelpers._

  def sort(document :Document) :StreamObservable = new StreamObservable(findObservable.sort(document))
  def projection(document :Document) :StreamObservable = new StreamObservable(findObservable.projection(document))

  def limit(n :Int) :StreamObservable = new StreamObservable(findObservable.limit(n))
  def skip(n :Int) :StreamObservable = new StreamObservable(findObservable.skip(n))

  def toFS2Stream() :fs2.Stream[IO, MongoResult[Document]] = fromObservable(findObservable)
}

