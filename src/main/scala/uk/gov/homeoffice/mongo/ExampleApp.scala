package uk.gov.homeoffice.mongo.sample

import cats.effect.IO
import org.mongodb.scala.bson.collection.immutable.Document

import uk.gov.homeoffice.mongo.{*, given}
import uk.gov.homeoffice.mongo.casbah.{*, given}
import uk.gov.homeoffice.mongo.repository.*
import uk.gov.homeoffice.mongo.model.*
import uk.gov.homeoffice.mongo.model.syntax.*
import uk.gov.homeoffice.mongo.model.syntax.*

import scala.reflect.ClassTag

import scala.concurrent.Future

object ExampleApp extends App {
  import org.mongodb.scala.given
  import org.mongodb.scala.result.*
  import cats.effect.unsafe.implicits.global
  println("test app started")

  // connect to localhost test db
  val globalDatabaseConnection :MongoConnection = MongoConnector.connect(
    "mongodb://localhost/example",
    "ExampleApp",
    false,
    "example"
  )

  /*
   *
   * Example: Basic database operations with fs2 streaming support
   *
  */

  // Write a record into the database (remember waiting on the future is important)
  scala.concurrent.Await.result(
    globalDatabaseConnection.mongoCollection[Document]("exampleLogins").insertOne(Document("hello" -> true)).head(),
    scala.concurrent.duration.Duration.Inf
  )

  val retval: Future[Option[Document]] = globalDatabaseConnection
    .mongoCollection[Document]("exampleLogins")
    .find(Document.empty)
    .sort(Document("hello" -> 1))
    .limit(1)
    .toSingle()
    .toFutureOption()

  println("search with delay returned: " + scala.concurrent.Await.result(retval, scala.concurrent.duration.Duration.Inf))

  // turn a collection into a repository and gain access to fs2 features.
  val basicBookRepository = new MongoStreamRepository(globalDatabaseConnection, "books", List("_id"))

  val allBooks: List[MongoResult[Document]] = basicBookRepository.all().compile.toList.unsafeRunSync()
  println(s"All Books: ${allBooks}")

  val miceAndMen: List[MongoResult[Document]] = basicBookRepository.find(Document("title" -> "Mice and Men")).toFS2Stream().compile.toList.unsafeRunSync()
  println(s"Search for one book: ${miceAndMen}")

  /*
   *
   * Example: using a repository with circe based conversion between types
   *
  */

  // bind some class to a repository to gain access to automatic conversion
  import io.circe._
  import io.circe.parser._
  import io.circe.generic._
  import io.circe.generic.auto._
  import io.circe.syntax._
  println("here")

  def bookToJson(book :Book) :Either[MongoError, io.circe.Json] = { Right(book.asJson) }
  def jsonToBook(json :io.circe.Json) :Either[MongoError, Book] = { decode[Book](json.spaces4) match {
    case Left(circeExc) => Left(MongoError(s"Unable to turn json into a book: ${circeExc.getMessage} (from JSON: ${json.spaces4})"))
    case Right(book) => Right(book)
  }}

  val autoBookRepository: MongoObjectRepository[Book] = new MongoObjectRepository[Book](new MongoJsonRepository(basicBookRepository)) {
    def jsonToObject(json :Json) :MongoResult[Book] = jsonToBook(json)
    def objectToJson(obj :Book) :MongoResult[Json] = bookToJson(obj)
  }

  // insert a book
  val saveResult: MongoResult[Json] = autoBookRepository.insertOne(Book("The Davinci Code", "Mike row", "743927492")).unsafeRunSync()
  println(s"Saving Davinci code returned: $saveResult")

  // read a book
  val daVinci: MongoResult[Option[Book]] = autoBookRepository.findOne(Json.obj("title" -> Json.fromString("The Davinci Code"))).unsafeRunSync()
  daVinci match {
    case Left(mongoError) => println(s"Unable to reinflate daVinci: $mongoError")
    case Right(None) => println(s"Davinci book not found")
    case Right(Some(book)) => println(s"Got this book: $book")
  }

  /*
   *
   * Example: Using the Casbah Interface!
   *
  */

  val casbahRepo = new MongoCasbahRepository(new MongoJsonRepository(basicBookRepository))
  val mongoRecord :Option[MongoDBObject] = casbahRepo.find(MongoDBObject("title" -> "The Davinci Code")).toList().headOption
  println(s"Casbah MongoDBObject style access: $mongoRecord")

  val salatRepo: MongoCasbahSalatRepository[Book] = new MongoCasbahSalatRepository[Book](casbahRepo) {
    def toMongoDBObject(a :Book) :MongoResult[MongoDBObject] = Right(MongoDBObject("author" -> a.author, "title" -> a.title, "isbn" -> a.isbn))
    def fromMongoDBObject(mongoDBObject :MongoDBObject) :MongoResult[Book] =
      (for {
        author <- mongoDBObject.getAs[String]("author")
        title <- mongoDBObject.getAs[String]("title")
        isbn <- mongoDBObject.getAs[String]("isbn")
      } yield { Book(author, title, isbn) }) match {
        case None => Left(MongoError(s"Unable to reflate book"))
        case Some(book) => Right(book)
      }
  }

  val aliceInWonderland: Book = salatRepo.save(Book("Alice in Wonderland", "Carol", "678234832"))
  val changed: Book = aliceInWonderland.copy(isbn="86738921")

  salatRepo.save(changed)

  val casbahFindResults :List[Book] = salatRepo.find(casbah.MongoDBObject("title" -> "Alice in Wonderland")).toList()
  println(s"result count: ${casbahFindResults.length}")
  println(s"first result: ${casbahFindResults.headOption}")

  // example MongoDBObjects and how they translate to JSON

  val testObj: MongoDBObject = casbah.MongoDBObject("date" -> ("$gt" -> new java.util.Date()))
  testObj.put("deleted" -> false)
  testObj.put("_id" -> "12121")

  casbahRepo.distinct("title", MongoDBObject("isbn" -> MongoDBObject("$ne" -> "678234832"))).toList.foreach { e => println(s"DISTINCT TITLES: $e") }

  println("test app finished")

}

case class Book(title :String, author :String, isbn :String)

