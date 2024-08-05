package uk.gov.homeoffice.mongo.sample

import cats.effect.IO
import org.mongodb.scala.bson.collection.immutable.Document

import uk.gov.homeoffice.mongo._
import uk.gov.homeoffice.mongo.model._

import cats.effect.unsafe.implicits.global

object ExampleApp extends App {
  println("test app started")

  // connect to localhost test db
  val globalDatabaseConnection :MongoConnection = MongoConnector.connect(
    "mongodb://localhost/example",
    "ExampleApp",
    false,
    "example"
  )

  // Write a record into the database (remember waiting on the future is important)
  scala.concurrent.Await.result(globalDatabaseConnection.mongoCollection("exampleLogins").insertOne(Document(
    "hello" -> true
  )).head(), scala.concurrent.duration.Duration.Inf)

  // turn a collection into a repository and gain access to fs2 features.
  val basicBookRepository = new MongoStreamRepository(globalDatabaseConnection, "books", List("_id"))

  val allBooks = basicBookRepository.all().compile.toList.unsafeRunSync()
  println(s"All Books: ${allBooks}")

  val miceAndMen = basicBookRepository.find(Document("title" -> "Mice and Men")).compile.toList.unsafeRunSync()
  println(s"Search for one book: ${miceAndMen}")

  // bind some class to a repository to gain access to automatic conversion
  import io.circe._
  import io.circe.parser._
  import io.circe.generic._
  import io.circe.generic.auto._
  import io.circe.syntax._
  println("here")

  def bookToJson(book :Book) :Either[MongoError, io.circe.Json] = { Right(book.asJson) }
  def jsonToBook(json :io.circe.Json) :Either[MongoError, Book] = { decode[Book](json.spaces4) match {
    case Left(circeExc) => Left(MongoError(circeExc.toString))
    case Right(book) => Right(book)
  }}

  val autoBookRepository = new MongoObjectRepository[Book](new MongoJsonRepository(basicBookRepository)) {
    def jsonToObject(json :Json) :MongoResult[Book] = jsonToBook(json)
    def objectToJson(obj :Book) :MongoResult[Json] = bookToJson(obj)
  }

  // insert a book
  val saveResult = autoBookRepository.insertOne(Book("The Davinci Code", "Mike row", "743927492")).unsafeRunSync()
  println(s"Saving Davinci code returned: $saveResult")

  // read a book
  val daVinci = autoBookRepository.findOne(Json.obj("title" -> Json.fromString("The Davinci Code"))).unsafeRunSync()
  daVinci match {
    case Left(appError) => println(s"Unable to reinflate daVinci: $appError")
    case Right(None) => println(s"Davinci book not found")
    case Right(Some(book)) => println(s"Got this book: $book")
  }

  // use some MongoHelper functions such as date between when sorting functions.
  println("test app finished")

}

case class Book(title :String, author :String, isbn :String)

