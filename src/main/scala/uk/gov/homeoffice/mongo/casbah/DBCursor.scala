package uk.gov.homeoffice.mongo.casbah

import uk.gov.homeoffice.mongo.model._
import io.circe.Json
import uk.gov.homeoffice.mongo.model.syntax.MongoResult
import cats.effect.IO
import cats.effect.unsafe.implicits.global

sealed trait DBCursor[A] {

  def projection(projection :MongoDBObject) :DBCursor[A]
  def sort(orderBy :MongoDBObject) :DBCursor[A]

  def limit(n :Int) :DBCursor[A]
  def skip(n :Int) :DBCursor[A]

  /* the default map is the map you would expect - DBCursor[A] -> DBCursor[B].
   * It is lazy and only applies fn when realised. It can be used in chains like this:
   *     find().sort.map(fn).limit(1).skip(1).map(fn2).take(2).toList
  */
  def map[B](fn :A => B) :DBCursor[B]

  /* finally realise the query */
  def stream() :fs2.Stream[IO, A]
}

class DBCursorMongoDBObjectImpl(jsonObservable :JsonObservable) extends DBCursor[MongoDBObject] {

  def projection(projection :MongoDBObject) :DBCursor[MongoDBObject] =
    new DBCursorMongoDBObjectImpl(jsonObservable.projection(projection.toJson()))

  def sort(orderBy :MongoDBObject) :DBCursor[MongoDBObject] =
    new DBCursorMongoDBObjectImpl(jsonObservable.sort(orderBy.toJson()))

  /* limit is not the same as fs2Stream.take(). limit becomes part of the mongo command executed on the
   * database server. The query isn't executed and results realised until unsafeRunSync is called on
   * the fs2Stream.
   *
   * Thus:
   *    db.find(MongoDBObject.empty).limit(1).sort(1)
   *
   * does nothing on the db server. when you add .toList the Mongo command itself includes the limit + 
   * sort arguments.
  */

  def limit(n :Int) :DBCursor[MongoDBObject] = new DBCursorMongoDBObjectImpl(jsonObservable.limit(n))
  def skip(n :Int) :DBCursor[MongoDBObject] = new DBCursorMongoDBObjectImpl(jsonObservable.skip(n))

  def map[B](fn :MongoDBObject => B) :DBCursor[B] = {
    new DBCursorWrappedImpl[B, MongoDBObject](this, fn)
  }

  def stream() :fs2.Stream[IO, MongoDBObject] = jsonObservable.toFS2Stream().map {
    case Left(mongoError) => throw new Exception(s"MONGO EXCEPTION DBCursorMongoDBObject.apply: $mongoError")
    case Right(json) => MongoDBObject(json)
  }

}

class DBCursorWrappedImpl[A, B](dbCursor :DBCursor[B], fn :(B => A)) extends DBCursor[A] {

  def projection(projection :MongoDBObject) :DBCursor[A] = new DBCursorWrappedImpl[A, B](dbCursor.projection(projection), fn)
  def sort(orderBy :MongoDBObject) :DBCursor[A] = new DBCursorWrappedImpl[A, B](dbCursor.sort(orderBy), fn)
  def limit(n :Int) :DBCursor[A] = new DBCursorWrappedImpl[A, B](dbCursor.limit(n), fn)
  def skip(n :Int) :DBCursor[A] = new DBCursorWrappedImpl[A, B](dbCursor.skip(n), fn)

  def stream() :fs2.Stream[IO, A] = dbCursor.stream().map(fn)

  def map[C](fnAC :A => C) :DBCursor[C] = {
    new DBCursorWrappedImpl[C, A](this, fnAC)
  }
}

object DBCursor {

  implicit class DBCursorOps[A](val underlying :DBCursor[A]) extends AnyVal {
    def toList() :List[A] =
      underlying.stream().compile.toList.unsafeRunSync()

    def headOption() :Option[A] =
      underlying.limit(1).stream().take(1).compile.toList.unsafeRunSync().headOption

    // Calling drain here means we never end up with O(n) records.
    def foreach(fn: A => Unit) :Unit =
      underlying.stream().map(fn).compile.drain.unsafeRunSync()

    def mapS[B](fn: A => B) :List[B] =
      underlying.stream().map(fn).compile.toList.unsafeRunSync()
  }

}

