package uk.gov.homeoffice.mongo.repository

import cats.effect.IO

import org.specs2.mutable.Specification
import org.specs2.matcher.Scope

import org.bson.types.ObjectId
import org.mongodb.scala.bson.Document
import com.mongodb.client.result.InsertOneResult

import uk.gov.homeoffice.mongo.TestMongo
import uk.gov.homeoffice.mongo.model.MongoError
import uk.gov.homeoffice.mongo.model.syntax._

import cats.effect.unsafe.implicits.global
import uk.gov.homeoffice.mongo.MongoConnection
import org.mongodb.scala.documentToUntypedDocument

class MongoStreamRepositorySpec extends Specification {
  
  sequential

  class Context extends Scope {
    val testConnection: MongoConnection = TestMongo.testConnection()

    val streamRepo = new MongoStreamRepository(
      testConnection,
      "streamTestTable",
      primaryKeys=List("_id")
    )
  }

  "MongoStreamRepository" should {

    "insertOne and return a IO[Right[InsertOneResult]]" in new Context {

      val result :IO[MongoResult[InsertOneResult]] = streamRepo.insertOne(Document("test" -> 1))

      result.unsafeRunSync() must beRight

    }

    "insertOne and return IO[Left[MongoError]] on failure" in new Context {
      val dupe = Document("_id" -> false)
      streamRepo.insertOne(dupe).unsafeRunSync()
      val result :MongoResult[InsertOneResult] = streamRepo.insertOne(dupe).unsafeRunSync()

      result must beLeft
      result.left.get.message must contain ("duplicate key error")
    }

    "save should return right" in new Context {
      val myRecord = Document("_id" -> "43", "name" -> "A")
      streamRepo.save(myRecord).unsafeRunSync()

      val myUpdatedRecord = Document("_id" -> "43", "name" -> "A")
      streamRepo.save(myUpdatedRecord).unsafeRunSync()

      streamRepo.countDocuments(Document()).unsafeRunSync() must beRight(1)
    }

    /* Here, after implementing this, I learn that Mongo doesn't support
     * combined keys. The _id can be nested objects in this scenario,
     * e.g. { _id: { a: 1, b: 2 }}
     *
     * Thus whilst I thought the test would result in list 45, 65
     * it only results in list 65.
    "save should use honour primary keys" in {
      val testConnection2 = TestMongo.testConnection()

      val streamRepoWith2PK = new MongoStreamRepository(
        testConnection2,
        "streamTestTableNew",
        primaryKeys=List("_id", "name")
      )

      val id = new ObjectId()
      println(s"object id: $id")

      val myRecord = Document("_id" -> id, "name" -> "A", "value" -> 23)
      streamRepoWith2PK.save(myRecord).unsafeRunSync() must beRight

      val updatedRecord = Document("_id" -> id, "name" -> "A", "value" -> 45)
      streamRepoWith2PK.save(updatedRecord).unsafeRunSync() must beRight

      val differentRecord = Document("_id" -> id, "name" -> "B", "value" -> 65)
      streamRepoWith2PK.save(differentRecord).unsafeRunSync() must beRight

      val results = streamRepoWith2PK.find(Document()).toFS2Stream().compile.toList.unsafeRunSync

      results.collect { case Right(i) => i.get("value").get.asInt64() }.sorted mustEqual List(45, 65)
    }*/

    "save should replace objects, not merge them" in new Context {
      val id = new ObjectId()

      val myRecord = Document("_id" -> id, "name" -> "A", "extraValue" -> 23)
      streamRepo.save(myRecord).unsafeRunSync() must beRight

      val updatedRecord = Document("_id" -> id, "name" -> "B") // extraValue missing
      streamRepo.save(updatedRecord).unsafeRunSync() must beRight

      val savedRecord :Either[MongoError, Option[Document]] = streamRepo.findOne(Document()).unsafeRunSync()

      savedRecord.toOption.flatten must beSome
      val doc = savedRecord.toOption.flatten.get
      doc.containsKey("extraValue") must beFalse
    }

    "aggregate works" in new Context {
      streamRepo.save(Document("aggregate" -> "StreamTest", "numeric" -> 66)).unsafeRunSync() must beRight
      streamRepo.save(Document("aggregate" -> "StreamTest", "numeric" -> 33)).unsafeRunSync() must beRight

      val result = streamRepo.aggregate(List(
        Document("$match" -> Document("aggregate" -> "StreamTest")),
        Document("$group" -> Document("_id" -> "0", "total" -> Document("$sum" -> "$numeric")))
      ))

      val listResult = result.compile.toList.unsafeRunSync()
      listResult.length mustEqual 1
      val resultDocument = listResult.head.right.get
      resultDocument.get("total").map(_.asNumber.intValue) must beSome(99)
    }

    "distinct basic scenario works" in new Context {
      streamRepo.save(Document("distinct" -> "StreamTest", "type" -> "ALPHA")).unsafeRunSync() must beRight
      streamRepo.save(Document("distinct" -> "StreamTest", "type" -> "OMEGA")).unsafeRunSync() must beRight
      streamRepo.save(Document("distinct" -> "StreamTest", "type" -> "OMEGA")).unsafeRunSync() must beRight
      streamRepo.save(Document("distinct" -> "StreamTest", "type" -> "ALPHA")).unsafeRunSync() must beRight

      val resultStream = streamRepo.distinct("type", Document("distinct" -> "StreamTest"))
      val rawResults = resultStream.compile.toList.unsafeRunSync().collect { case Right(doc) => doc }
      rawResults.sorted mustEqual List("ALPHA", "OMEGA")
    }

    "injecting a broken command results in a stream with a short-circuited error" in new Context {
      import scala.util.Try

      val qry = Try(streamRepo.find(Document("$exists" -> 1)).sort(Document("$nin" -> false)).limit(-1)).toEither

      val realised :Either[Throwable, List[Either[Throwable, MongoResult[Document]]]] = Try(qry.right.get.toFS2Stream().attempt.compile.toList.unsafeRunSync()).toEither
      realised must beRight

      val mongoResult :MongoResult[Document] = realised.right.get.headOption.get.right.get
      mongoResult must beLeft

      val errorString = mongoResult.left.get.message
      errorString must contain("Streaming error")
      errorString must contain("Command failed with error 2 (BadValue)")
      errorString must contain("unknown top level operator: $exists")
    }

  }
}

