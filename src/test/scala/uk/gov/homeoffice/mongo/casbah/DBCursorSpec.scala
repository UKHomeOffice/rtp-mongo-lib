package uk.gov.homeoffice.mongo.casbah

import cats.effect._
import uk.gov.homeoffice.mongo.model._
import org.mongodb.scala.bson.Document
import uk.gov.homeoffice.mongo.model.syntax.MongoResult
import uk.gov.homeoffice.mongo.repository._
import io.circe.Json

import org.specs2.mutable.Specification
import org.specs2.matcher.Scope

class DBCursorSpec extends Specification {

  def mkDocument(value :String) :Document = Document("value" -> value)
  def getDocumentValue(d :Document) :String = d.get[org.bson.BsonString]("value").get.getValue

  class MockStreamObservable[A](callback :(String) => Unit, initialData :List[Document]) extends StreamObservable(null) {

    var data :List[MongoResult[Document]] = initialData.map(Right(_))

    override def sort(document :Document) :StreamObservable = {
      callback("SORT INVOKED")
      this
    }

    override def projection(document :Document) :StreamObservable = {
      callback("PROJECTION INVOKED")
      this
    }

    override def limit(n :Int) :StreamObservable = {
      callback(s"LIMITED TO $n")
      data = data.take(n)
      this
    }

    override def skip(n :Int) :StreamObservable = {
      callback(s"SKIP TO $n")
      data = data.drop(n)
      this
    }

    override def toFS2Stream() :fs2.Stream[IO, MongoResult[Document]] = {
      callback(s"FROMITERATOR CALLED")
      // fs2 is faster with batching but 1 as the cache size helps
      // verify behaviour.
      fs2.Stream.fromIterator[IO](data.iterator, 1) 
    }
  }

  class Context extends Scope {

    /* To ensure the performance of the DBCursor, we have a little
     * list to capture all the functions called in the underlying class.
     * Using this makes sure we don't inflate the whole list by accident
     * (causing OOM problems) or iterate through items unneccessarily
     * (causing CPU exhaustion).

     After each test you can check the "events" list to ensure only the
     calls you expected to happen actually did happen.
    */

    val events = new scala.collection.mutable.ListBuffer[String]()
    def appendEvent(e :String) :Unit = events.append(e)

    def jsonToDocumentWithEvent(json :Json) :MongoResult[Document] = {
      appendEvent(s"JSON TO DOCUMENT")
      MongoJsonRepository.jsonToDocument(json)
    }

    def documentToJsonWithEvent(document :Document) :MongoResult[Json] = {
      appendEvent(s"DOCUMENT TO JSON")
      MongoJsonRepository.documentToJson(document)
    }

  }

  "DBCursorSpec" should {

    "be constructable from a JsonObservable" in new Context {

      val testData :List[Document] = List("hello", "world", "test").map(mkDocument)

      val mockJsonObservable = new JsonObservableImpl(
        new MockStreamObservable(appendEvent _, testData), MongoJsonRepository.jsonToDocument, MongoJsonRepository.documentToJson)

      val testDBCursor = new DBCursorMongoDBObjectImpl(mockJsonObservable)

      val actualResult = testDBCursor.toList()

      actualResult mustEqual List(
        MongoDBObject("value" -> "hello"),
        MongoDBObject("value" -> "world"),
        MongoDBObject("value" -> "test")
      )

    }

    "have a working skip function" in new Context {
      val testData :List[Document] = List("hello", "world", "test").map(mkDocument)

      val mockJsonObservable = new JsonObservableImpl(
        new MockStreamObservable(appendEvent _, testData), MongoJsonRepository.jsonToDocument, MongoJsonRepository.documentToJson)

      val testDBCursor = new DBCursorMongoDBObjectImpl(mockJsonObservable)

      val actualResult = testDBCursor.skip(1).toList()

      actualResult mustEqual List(
        /* SKIPPED! MongoDBObject("value" -> "hello"), */
        MongoDBObject("value" -> "world"),
        MongoDBObject("value" -> "test")
      )

      events mustEqual List(
        "SKIP TO 1",
        "FROMITERATOR CALLED"
      )

    }

    /*
     * Performance on this test is important. We must ensure that only the first
     * record is evaulated and none of the rest are. If they, in production, where
     * the datasets are big, the system could OOM (which has happened with the first
     * release of this code)
     */
    "have a working limit function" in new Context {
      val testData :List[Document] = List("hello", "world", "test").map(mkDocument)

      val mockJsonObservable = new JsonObservableImpl(
        new MockStreamObservable(appendEvent _, testData), jsonToDocumentWithEvent, documentToJsonWithEvent)

      val testDBCursor = new DBCursorMongoDBObjectImpl(mockJsonObservable)

      val actualResult = testDBCursor.limit(1).toList()

      actualResult mustEqual List(
        MongoDBObject("value" -> "hello")
        /* IGNORED: MongoDBObject("value" -> "world"), */
        /* IGNORED: MongoDBObject("value" -> "test") */
      )

      events mustEqual List(
        "LIMITED TO 1",
        "FROMITERATOR CALLED",
        "DOCUMENT TO JSON" // crutically important to performance this only appears once!!
      )

    }

    "supports a lazy map function (third elem not invoked!)" in new Context {
      val testData :List[Document] = List("CH33S3", "P3T3E", "L33T", "bad").map(mkDocument)

      val mockJsonObservable = new JsonObservableImpl(
        new MockStreamObservable(appendEvent _, testData), jsonToDocumentWithEvent, documentToJsonWithEvent)

      val testDBCursor = new DBCursorMongoDBObjectImpl(mockJsonObservable)

      def testFunction(mongoDBObject :MongoDBObject) :MongoDBObject = {
        appendEvent("REWRITING DOCUMENT")
        val s1 = mongoDBObject.as[String]("value")
        val s2 = s1.replaceAllLiterally("E", "X")
        val s3 = s2.replaceAllLiterally("3", "E")
        MongoDBObject("value" -> s3)
      }

      // map written before limit, to ensure map is lazy!
      // limit runs on the db server, then map on the realised results
      // but important take-away is that testFunction never run against "bad" test entry.
      val actualResult :List[MongoDBObject] = testDBCursor.map(testFunction).limit(3).toList() 

      // Test function changes E to X, then 3 to E so L33T becomes LEET.
      // but if fn is called twice, by accident, L33T becomes LXXT so this
      // test prevents bugs where fn being evaluated on a record twice by mistake.

      actualResult mustEqual List(
        MongoDBObject("value" -> "CHEESE"),
        MongoDBObject("value" -> "PETEX"),
        MongoDBObject("value" -> "LEET")
      )

      events mustEqual List(
        "LIMITED TO 3",
        "FROMITERATOR CALLED",
        // If these are called 4 times
        // app has a serious performance problem.
        "DOCUMENT TO JSON",
        "REWRITING DOCUMENT",
        "DOCUMENT TO JSON",
        "REWRITING DOCUMENT",
        "DOCUMENT TO JSON",
        "REWRITING DOCUMENT"
      )
    }

    "supports an effecient mapS function" in new Context {
      val testData :List[Document] = List("CH33S3", "P3T3E", "L33T", "bad").map(mkDocument)

      val mockJsonObservable = new JsonObservableImpl(
        new MockStreamObservable(appendEvent _, testData), jsonToDocumentWithEvent, documentToJsonWithEvent)

      val testDBCursor = new DBCursorMongoDBObjectImpl(mockJsonObservable)

      def testFunction(mongoDBObject :MongoDBObject) :MongoDBObject = {
        appendEvent("REWRITING DOCUMENT")
        val s1 = mongoDBObject.as[String]("value")
        val s2 = s1.replaceAllLiterally("E", "X")
        val s3 = s2.replaceAllLiterally("3", "E")
        MongoDBObject("value" -> s3)
      }

      val actualResult :List[MongoDBObject] = testDBCursor.limit(3).mapS(testFunction)

      actualResult mustEqual List(
        MongoDBObject("value" -> "CHEESE"),
        MongoDBObject("value" -> "PETEX"),
        MongoDBObject("value" -> "LEET")
      )

      events mustEqual List(
        "LIMITED TO 3",
        "FROMITERATOR CALLED",
        // If these are called 4 times
        // app has a serious performance problem.
        "DOCUMENT TO JSON",
        "REWRITING DOCUMENT",
        "DOCUMENT TO JSON",
        "REWRITING DOCUMENT",
        "DOCUMENT TO JSON",
        "REWRITING DOCUMENT"
      )
    }

    "supports an effecient foreach function" in new Context {
      val testData :List[Document] = List("CH33S3", "P3T3E", "L33T", "bad").map(mkDocument)

      val mockJsonObservable = new JsonObservableImpl(
        new MockStreamObservable(appendEvent _, testData), jsonToDocumentWithEvent, documentToJsonWithEvent)

      val testDBCursor = new DBCursorMongoDBObjectImpl(mockJsonObservable)

      def testFunction(mongoDBObject :MongoDBObject) :MongoDBObject = {
        appendEvent("REWRITING DOCUMENT")
        val s1 = mongoDBObject.as[String]("value")
        val s2 = s1.replaceAllLiterally("E", "X")
        val s3 = s2.replaceAllLiterally("3", "E")
        MongoDBObject("value" -> s3)
      }

      val actualResult :List[MongoDBObject] = testDBCursor.limit(3).mapS(testFunction)

      actualResult mustEqual List(
        MongoDBObject("value" -> "CHEESE"),
        MongoDBObject("value" -> "PETEX"),
        MongoDBObject("value" -> "LEET")
      )

      events mustEqual List(
        "LIMITED TO 3",
        "FROMITERATOR CALLED",
        // If these are called 4 times
        // app has a serious performance problem.
        "DOCUMENT TO JSON",
        "REWRITING DOCUMENT",
        "DOCUMENT TO JSON",
        "REWRITING DOCUMENT",
        "DOCUMENT TO JSON",
        "REWRITING DOCUMENT"
      )
    }

  }
}

