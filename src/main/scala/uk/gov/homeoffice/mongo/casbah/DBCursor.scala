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
  def take(n :Int) :DBCursor[A]

  def apply() :List[A]
  def toList() :List[A]

  def map[B](fn :A => B) :DBCursor[B]
}

class DBCursorMongoDBObjectImpl(jsonObservable :JsonObservable) extends DBCursor[MongoDBObject] {

  def projection(projection :MongoDBObject) :DBCursor[MongoDBObject] =
    new DBCursorMongoDBObjectImpl(jsonObservable.projection(projection.toJson))

  def sort(orderBy :MongoDBObject) :DBCursor[MongoDBObject] =
    new DBCursorMongoDBObjectImpl(jsonObservable.sort(orderBy.toJson))

  def limit(n :Int) :DBCursor[MongoDBObject] = new DBCursorMongoDBObjectImpl(jsonObservable.limit(n))
  def skip(n :Int) :DBCursor[MongoDBObject] = new DBCursorMongoDBObjectImpl(jsonObservable.skip(n))
  def take(n :Int) :DBCursor[MongoDBObject] = limit(n)

  def apply() :List[MongoDBObject] = jsonObservable.toFS2Stream().map {
    case Left(mongoError) => throw new Exception(s"MONGO EXCEPTION DBCursorMongoDBObject.apply: $mongoError")
    case Right(json) => MongoDBObject(json)
  }.compile.toList.unsafeRunSync()

  def toList() :List[MongoDBObject] = apply()

  def map[B](fn :MongoDBObject => B) :DBCursor[B] = {
    new DBCursorWrappedImpl[B, MongoDBObject](this, fn)
  }
}

class DBCursorImpl[A](dbCursor :DBCursor[A]) extends DBCursor[A] {

  def projection(projection :MongoDBObject) :DBCursor[A] =
    new DBCursorImpl[A](dbCursor.projection(projection))

  def sort(orderBy :MongoDBObject) :DBCursor[A] =
    new DBCursorImpl[A](dbCursor.sort(orderBy))

  def limit(n :Int) :DBCursor[A] = new DBCursorImpl[A](dbCursor.limit(n))
  def skip(n :Int) :DBCursor[A] = new DBCursorImpl[A](dbCursor.skip(n))
  def take(n :Int) :DBCursor[A] = limit(n)

  def apply() :List[A] = dbCursor.toList()
  def toList() :List[A] = apply()

  def map[B](fn :A => B) :DBCursor[B] = {
    new DBCursorWrappedImpl[B, A](this, fn)
  }
}

class DBCursorWrappedImpl[A, B](dbCursor :DBCursor[B], fn :(B => A)) extends DBCursor[A] {

  def projection(projection :MongoDBObject) :DBCursor[A] = new DBCursorWrappedImpl[A, B](dbCursor.projection(projection), fn)
  def sort(orderBy :MongoDBObject) :DBCursor[A] = new DBCursorWrappedImpl[A, B](dbCursor.sort(orderBy), fn)
  def limit(n :Int) :DBCursor[A] = new DBCursorWrappedImpl[A, B](dbCursor.limit(n), fn)
  def skip(n :Int) :DBCursor[A] = new DBCursorWrappedImpl[A, B](dbCursor.skip(n), fn)
  def take(n :Int) :DBCursor[A] = limit(n)

  def apply() :List[A] = dbCursor.toList().map(fn)
  def toList() :List[A] = apply()

  def map[C](fnAC :A => C) :DBCursor[C] = {
    new DBCursorWrappedImpl[C, A](this, fnAC)
  }
}

class DBCursorError[A](mongoError :MongoError) extends DBCursor[A] {

  def projection(projection :MongoDBObject) :DBCursor[A] = this
  def sort(orderBy :MongoDBObject) :DBCursor[A] = this
  def limit(n :Int) :DBCursor[A] = this
  def skip(n :Int) :DBCursor[A] = this
  def take(n :Int) :DBCursor[A] = limit(n)

  def apply() :List[A] = throw new Exception(s"MONGO DB CURSOR EXCEPTION: $mongoError")
  def toList() :List[A] = throw new Exception(s"MONGO DB CURSOR EXCEPTION: $mongoError")

  def map[B](fn :A => B) :DBCursor[B] = new DBCursorError[B](mongoError)
}
