package uk.gov.homeoffice.mongo.casbah

import uk.gov.homeoffice.mongo.repository._
import uk.gov.homeoffice.mongo.TestMongo

import org.bson.types.ObjectId
import org.joda.time.DateTime
import io.circe.Json

import scala.collection.mutable
import scala.util.Try

import org.specs2.mutable.Specification

import org.mongodb.scala.bson.Document
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import uk.gov.homeoffice.mongo.MongoConnection

class MongoCasbahRepositorySpec extends Specification {
  import org.mongodb.scala.given

  sequential

  val testConnection: MongoConnection = TestMongo.testConnection()

  val casbahRepoTest = new MongoCasbahRepository(
    new MongoJsonRepository(
      new MongoStreamRepository(
        testConnection,
        "casbahTestTable",
        primaryKeys=List("_id")
      )
    )
  )

  "MongoCasbahRepository" should {

    "insertOne works" in {

      val hardcodedId = "66d5e8a4baf7a68b82ba2bad"
      
      val result = casbahRepoTest.insertOne(MongoDBObject(
        "_id" -> new ObjectId(hardcodedId),
        "hello" -> "world"
      ))

      result.wasAcknowledged() mustEqual true
      result.getInsertedId().toHexString mustEqual hardcodedId

      // use native code to ensure backend serialisation is as expected
      val expectedDocument = Document(
        "_id" -> new ObjectId(hardcodedId),
        "hello" -> "world"
      )
      
      val future = testConnection.database.getCollection("casbahTestTable")
        .find(expectedDocument)
        .toSingle()
        .toFutureOption()

      Await.result(future, Duration.Inf) must beSome(expectedDocument)

    }

    "save works" in {
      /* save depends on MongoStreamRepository being configured with
       * the primaryKey set to the correct value.
       * */

      val hardcodedId = "66d5e8b4baf7a68b82912501"

      val myObject = MongoDBObject(
        "_id" -> new ObjectId(hardcodedId),
        "name" -> "Jessica"
      )

      val firstWriteResult = casbahRepoTest.save(myObject)

      firstWriteResult.getUpsertedId() must beSome(new ObjectId(hardcodedId))
      firstWriteResult.wasAcknowledged() must beTrue
      firstWriteResult.getMatchedCount() mustEqual 0
      firstWriteResult.getModifiedCount() mustEqual 0
      firstWriteResult.getN() mustEqual 0
      firstWriteResult.isUpdateOfExisting() must beFalse

      myObject += ("extra", "cheese")

      val secondWriteResult = casbahRepoTest.save(myObject) // This is an overwrite.

      secondWriteResult.getUpsertedId() must beNone
      secondWriteResult.wasAcknowledged() must beTrue
      secondWriteResult.getMatchedCount() mustEqual 1
      secondWriteResult.getModifiedCount() mustEqual 1
      secondWriteResult.getN() mustEqual 1
      secondWriteResult.isUpdateOfExisting() must beTrue

      val expectedDocument = Document(
        "_id" -> new ObjectId(hardcodedId),
        "name" -> "Jessica",
        "extra" -> "cheese"
      )
      
      val future = testConnection.database.getCollection("casbahTestTable")
        .find(expectedDocument)
        .toSingle()
        .toFutureOption()

      Await.result(future, Duration.Inf) must beSome(expectedDocument)

    }

    "save without _id returns upsertedId" in {

      val myObject = MongoDBObject("name" -> "forever")

      val firstWriteResult = casbahRepoTest.save(myObject)

      firstWriteResult.getUpsertedId().isDefined must beTrue
    }

    "findOne works" in {

      val id = new ObjectId()
      
      /* save with native id */
      val myObject = MongoDBObject(
        "_id" -> id,
        "name" -> "chains"
      )
      casbahRepoTest.save(myObject)

      val findResult = casbahRepoTest.findOne(myObject)

      findResult must beSome(MongoDBObject(
        "_id" -> MongoDBObject("$oid" -> id.toHexString),
        "name" -> "chains"
      ))

      findResult.flatMap(_.getAs[String]("name")) must beSome("chains")
      findResult.flatMap(_.getAs[ObjectId]("_id")) must beSome(id)

    }

    "findOne can return None" in {
      val findResult = casbahRepoTest.findOne(MongoDBObject("673496392sdnsao" -> true))

      findResult must beNone
    }

    "find works" in {
      casbahRepoTest.save(MongoDBObject("query" -> 1))
      casbahRepoTest.save(MongoDBObject("query" -> 2))
      casbahRepoTest.save(MongoDBObject("query" -> 3))
      casbahRepoTest.save(MongoDBObject("query" -> 4))
      
      val findList = casbahRepoTest.find(MongoDBObject("query" -> 2))

      findList.toList().length mustEqual 1

      /* check no use of iterators which means toList is empty on subsequent calls */
      findList.toList().length mustEqual 1

      findList.toList().headOption.flatMap(_.getAs[Int]("query")) must beSome(2)

    }

    "find with mongo operators works" in {
      casbahRepoTest.save(MongoDBObject("listCheck" -> 1))
      casbahRepoTest.save(MongoDBObject("listCheck" -> 2))
      casbahRepoTest.save(MongoDBObject("listCheck" -> 3))
      casbahRepoTest.save(MongoDBObject("listCheck" -> 4))

      val findList = casbahRepoTest.find(MongoDBObject("listCheck" -> MongoDBObject("$gte" -> 3)))

      findList.toList().length mustEqual 2
      findList.toList().map(_.as[Int]("listCheck")) mustEqual List(3,4)
    }

    "find works on nested MongoDBObjects" in {
      casbahRepoTest.save(MongoDBObject("nestedCheck" -> MongoDBObject("name" -> "Alvin")))
      casbahRepoTest.save(MongoDBObject("nestedCheck" -> MongoDBObject("name" -> "Simon")))
      casbahRepoTest.save(MongoDBObject("nestedCheck" -> MongoDBObject("name" -> "Theodor")))

      val findList = casbahRepoTest.find(
        MongoDBObject("$or" -> MongoDBList(
          MongoDBObject("nestedCheck.name" -> "Alvin"),
          MongoDBObject("nestedCheck.name" -> "Simon")
        ))
      )

      findList.toList().length mustEqual 2
      // relies on dotted notation in MongoDBObject working
      findList.toList().map(_.as[String]("nestedCheck.name")) mustEqual List("Alvin", "Simon")
    }

    "find.sort on a DBCursor works" in {
      casbahRepoTest.save(MongoDBObject("sortables" -> "pa"))
      casbahRepoTest.save(MongoDBObject("sortables" -> "pc"))
      casbahRepoTest.save(MongoDBObject("sortables" -> "pb"))

      val ascendingResults = casbahRepoTest.find(MongoDBObject("sortables" -> MongoDBObject("$exists" -> 1))).sort(MongoDBObject("sortables" -> 1))

      ascendingResults.toList().map(_.as[String]("sortables")) mustEqual List("pa", "pb", "pc")

      val descendingResults = casbahRepoTest.find(MongoDBObject("sortables" -> MongoDBObject("$exists" -> 1))).sort(MongoDBObject("sortables" -> -1))

      descendingResults.toList().map(_.as[String]("sortables")) mustEqual List("pc", "pb", "pa")
    }

    "find.limit on a DBCursor works" in {
      casbahRepoTest.save(MongoDBObject("erin" -> "p"))
      casbahRepoTest.save(MongoDBObject("erin" -> "p"))
      casbahRepoTest.save(MongoDBObject("erin" -> "p"))

      val listOfTwo = casbahRepoTest.find(MongoDBObject("erin" -> "p")).limit(2).toList()

      listOfTwo.length mustEqual 2
    }

    "You can reuse unrealised DBCursors (also skip works)" in {
      casbahRepoTest.save(MongoDBObject("directory" -> 1))
      casbahRepoTest.save(MongoDBObject("directory" -> 2))
      casbahRepoTest.save(MongoDBObject("directory" -> 3))
      casbahRepoTest.save(MongoDBObject("directory" -> 4))
      casbahRepoTest.save(MongoDBObject("directory" -> 5))

      val unrealisedCursor :DBCursor[MongoDBObject] = casbahRepoTest.find(MongoDBObject("directory" -> MongoDBObject("$exists" -> true))).sort(MongoDBObject("directory" -> 1))

      val realisedResultset :List[MongoDBObject] = unrealisedCursor.toList()
      realisedResultset.length mustEqual 5

      val reusedCursorWithSkip :DBCursor[MongoDBObject] = unrealisedCursor.skip(2).limit(2)
      val realisedReusedCursor :List[MongoDBObject] = reusedCursorWithSkip.toList()

      realisedReusedCursor.length mustEqual 2
      realisedReusedCursor.map(_.as[Int]("directory")) mustEqual List(3,4)

    }

    "find.map works" in {
      casbahRepoTest.save(MongoDBObject("itemName" -> "hamburger", "price" -> 2.00d))

      def priceFromMongoDBObject(in :MongoDBObject) :Double = in.as[Double]("price")
      def addInflation(in :Double) :Double = in * 1.05d

      val burgerInflation :Option[Double] = casbahRepoTest.find(MongoDBObject("itemName" -> "hamburger"))
        .map(priceFromMongoDBObject) // changes DBCursor from underlying type of DBCursorMongoDBObject into DBCursorImpl
        .map(addInflation)           // applies a function lazily over the stream. Changes DBCursor to DBCursorWrappedImpl
        .toList()
        .headOption

      burgerInflation must beSome(2.1d)

    }

    "count works" in {
      casbahRepoTest.save(MongoDBObject("name" -> "landriano"))
      casbahRepoTest.save(MongoDBObject("name" -> "landriano"))
      casbahRepoTest.save(MongoDBObject("name" -> "landriano"))

      val result = casbahRepoTest.count(MongoDBObject("name" -> "landriano"))
      result mustEqual 3
    }

    "count on a missing table returns zero" in {
      val missingTable = new MongoCasbahRepository(
        new MongoJsonRepository(
          new MongoStreamRepository(
            testConnection,
            "missingTable",
            primaryKeys=List("_id")
          )
        )
      )

      val result = missingTable.count(MongoDBObject.empty())
      result mustEqual 0
    }

    "aggregate works" in {
      casbahRepoTest.save(MongoDBObject("testName" -> "aggTest", "x" -> 1, "y" -> 5)) //skipped
      casbahRepoTest.save(MongoDBObject("testName" -> "aggTest", "x" -> 2, "y" -> 6)) //skipped
      casbahRepoTest.save(MongoDBObject("testName" -> "aggTest", "x" -> 3, "y" -> 7)) // 10
      casbahRepoTest.save(MongoDBObject("testName" -> "aggTest", "x" -> 4, "y" -> 8)) // 12
      casbahRepoTest.save(MongoDBObject("testName" -> "aggTest", "x" -> 5, "y" -> 9)) // 14

      val result :List[Int] = casbahRepoTest.aggregate(List(
        MongoDBObject("$match" -> MongoDBObject("testName" -> "aggTest")),
        MongoDBObject("$sort" -> MongoDBObject("x" -> 1)),
        MongoDBObject("$skip" -> 2),
        MongoDBObject("$project" -> MongoDBObject("sum" -> MongoDBObject("$sum" -> MongoDBList("$x", "$y"))))
      )).toList.map(_.as[Int]("sum"))

      result mustEqual List(10, 12, 14)

    }

  }

}
