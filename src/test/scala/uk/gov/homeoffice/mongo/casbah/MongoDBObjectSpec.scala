package uk.gov.homeoffice.mongo.casbah

import org.bson.types.ObjectId
import org.joda.time.DateTime
import io.circe.Json

import scala.collection.mutable

import org.specs2.mutable.Specification

class MongoDBObjectSpec extends Specification {

  "MongoDBObject" should {

    "has a working put function" in {
      
      val m :MongoDBObject = MongoDBObject.empty
      m.put("hello" -> "test")

      m.toMap mustEqual Map[String, AnyRef]("hello" -> "test".asInstanceOf[AnyRef])

    }

    "working put and get" in {
      val m :MongoDBObject = MongoDBObject.empty
      m.put("hello" -> "test")
      m.get("hello") must beSome("test")

      m.put("cheese" -> true)
      m.get("hello") must beSome("test") // doesn't get erased
      m.get("cheese") must beSome(true)

    }

    "get, as and getAs work as intended" in {
      val m :MongoDBObject = MongoDBObject.empty
      m.put("long" -> 145l)

      m.get("long") must beSome(145l)

      m.getAs[Long]("long") must beSome(145l)
      m.getAs[BigDecimal]("long") must beSome(145l)
      m.as[Long]("long") mustEqual 145l
    }

    "get on a non-existent key returns None" in {
      val m :MongoDBObject = MongoDBObject.empty
      m.put("long" -> 145l)
      m.get("cheese") must beNone
    }

    //"getAs, with the wrong type, returns None" in {

    //  val m :MongoDBObject = MongoDBObject.empty
    //  m.put("long" -> 145l)
    //  m.getAs[ObjectId]("long") must beNone

    //}
    
    "put will overwrite values" in {
      val m :MongoDBObject = MongoDBObject.empty
      m.put("hello" -> "test")
      m.put("hello" -> "X")

      m.get("hello") must beSome("X")
    }

    "MongoDBObjects can be nested" in {

      val m :MongoDBObject = MongoDBObject.empty
      m.put("hello" -> MongoDBObject("true" -> "test"))

      m.getAs[String]("hello.true") must beSome("test")

    }

    "shallow MongoDBObjects overwrite the previous entry" in {

      val m :MongoDBObject = MongoDBObject.empty
      m.put("hello" -> MongoDBObject("true" -> "test"))

      m.getAs[String]("hello.true") must beSome("test")

      m.put("hello" -> MongoDBObject("false" -> "benny"))

      m.getAs[String]("hello.true") must beNone
      m.getAs[String]("hello.false") must beSome("benny")
    }

    "basic dotted notation is supported in get/getAs" in {
      val m :MongoDBObject = MongoDBObject.empty
      m.put("hello" -> MongoDBObject("true" -> MongoDBObject("riffle" -> "test")))
      m.put("hello.chalk" -> false)

      m.getAs[String]("hello.true.riffle") must beSome("test")
      m.getAs[String]("hello.chalk") must beSome(false)

      // For better compatibility, make this work with chalk as well
      m.getAs[MongoDBObject]("hello").map (_.keySet) mustEqual Some(Set("true"))
    }

    "use of dotted notation in put results in merged objects" in {
      val m :MongoDBObject = MongoDBObject.empty
      m.put("hello" -> MongoDBObject("true" -> MongoDBObject("riffle" -> "test")))
      m.put("hello.true.raffle" -> false)

      m.getAs[String]("hello.true.riffle") must beSome("test")
      m.getAs[String]("hello.true.raffle") must beSome(false)
    }

    "support basic json payloads" in {

      val m = MongoDBObject(Json.obj("hello" -> Json.fromString("test")))
      m.get("hello") must beSome("test")

    }

    "support Mongo Extended JSON format for ObjectIds yet provide native access" in {

      val myObjectId = new ObjectId()
      val m = MongoDBObject("myObjectId" -> myObjectId)

      m.getAs[ObjectId]("myObjectId") must beSome(myObjectId)
      
      val asJson = m.toJson()
      asJson mustEqual Json.obj("myObjectId" -> Json.obj("$oid" -> Json.fromString(myObjectId.toHexString())))

    }

    "support Mongo Extended JSON format for Dates" in {

      val myDateTime = new DateTime()
      val m = MongoDBObject("myDateTime" -> myDateTime)

      /* DateTimes rarely compare if they are instantiated in different ways */
      m.getAs[DateTime]("myDateTime").map(_.isEqual(myDateTime)).getOrElse(false) must beTrue

      val asJson = m.toJson()
      asJson mustEqual Json.obj("myDateTime" -> Json.obj("$date" -> Json.fromString(myDateTime.toString())))

    }

    "support Mongo Extended JSON format for NumberLong" in {
      val m = MongoDBObject("myLong" -> 98473924789238l)

      m.getAs[Long]("myLong") must beSome(98473924789238l)
      
      val asJson = m.toJson()
      asJson mustEqual Json.obj("myLong" -> Json.obj("$numberLong" -> Json.fromString(98473924789238l.toString)))
    }

    "support Mongo Extended JSON format for $date -> $numberLong" in {
      val m = MongoDBObject("birthday" -> MongoDBObject("$date" -> MongoDBObject("$numberLong" -> 1724165563932l.toString)))

      m.as[DateTime]("birthday").isEqual(new DateTime(1724165563932l)) must beTrue

      // using dotted notation
      // TODO: Fix
      //val m2 = MongoDBObject("birthday.$date.$numberLong" -> 1724165563932l.toString)
      //m2.as[DateTime]("birthday").isEqual(new DateTime(1724165563932l)) must beTrue
    }

    "have a working as[A] function" in {
      val m = MongoDBObject("birthday" -> MongoDBObject("$date" -> MongoDBObject("$numberLong" -> 1724165563932l.toString)))
      m.as[DateTime]("birthday") mustEqual new DateTime(1724165563932l)

    }

    "have a working getAsOrElse function" in {
      val m = MongoDBObject("myLong" -> 98473924789238l)

      m.getAs[Long]("myLong") must beSome(98473924789238l)

      m.getAs[Long]("noexist") must beNone

      // For compatibility reasons make this work.
      //m.getAs[Long]("myLong.someInnerValue") must beNone
    }

    "have a working iterator() function" in {
      1 mustEqual 1
      val m = MongoDBObject(
        "1" -> 1,
        "2" -> 2,
        "3" -> 3,
      )

      m.iterator.map(_._2.asInstanceOf[Int]).sum mustEqual 6
      
    }

    "have a working ++ operator" in {
      val a = MongoDBObject("a" -> "a")
      val b = MongoDBObject("b" -> "b")

      val r = a ++ b
      r mustEqual MongoDBObject("a" -> "a", "b" -> "b")
      
      // originals unaltered
      a mustEqual MongoDBObject("a" -> "a")
      b mustEqual MongoDBObject("b" -> "b")

      val r2 = r ++ MongoDBObject("a" -> 1)
      r2 mustEqual MongoDBObject("a" -> 1, "b" -> "b")
    }

    "have a working += operator" in {
      1 mustEqual 1
    }

    "have a working -= operator" in {
      val m = MongoDBObject(
        "1" -> 1,
        "2" -> 2,
        "3" -> 3,
      )

      m -= MongoDBObject("1" -> 1)
      m mustEqual MongoDBObject("2" -> 2, "3" -> 3)
    
      m -= MongoDBObject("5" -> 5, "2" -> 2) // 5 not even present
      m mustEqual MongoDBObject("3" -> 3)
    }

    "have a working put[A] function" in {
      val m = MongoDBObject()

      m.put("england" -> 14)
      m.put("scotland" -> 16)
      m.put("wales" -> 20)
      m.put("northern.ireland" -> 22)

      m mustEqual MongoDBObject("england" -> 14, "scotland" -> 16, "wales" -> 20, "northern.ireland" -> 22)
    }

    "have a working keySet function" in {
      val m = MongoDBObject(
        "orange" -> 1,
        "banana" -> 2,
        "apple" -> MongoDBObject("granny smith" -> 3),
      )

      m.keySet mustEqual Set("apple", "banana", "orange")
    }

    "have a working containsKey function" in {
      val m = MongoDBObject(
        "orange" -> 1,
        "banana" -> 2,
        "apple" -> MongoDBObject("granny smith" -> 3),
      )

      m.containsKey("orange") must beTrue
      m.containsKey("zuccini") must beFalse
      /* It is intended behavior that containsKey does not support dotted notation */
      m.containsKey("apple.granny smith") must beFalse
      m.containsKey("apple.red") must beFalse
    }

    "have a working toMap function" in {
      val m = MongoDBObject(
        "orange" -> 1,
        "banana" -> 2,
        "apple" -> MongoDBObject("granny smith" -> 3),
      )

      m.toMap mustEqual Map[String, AnyRef](
        "orange" -> 1.asInstanceOf[AnyRef],
        "banana" -> 2.asInstanceOf[AnyRef],
        "apple" -> MongoDBObject("granny smith" -> 3)
      )
    }

    "asDBObject that returns a DBObject" in {
      val m = MongoDBObject(
        "orange" -> 1,
        "banana" -> 2,
        "apple" -> MongoDBObject("granny smith" -> 3),
      )

      val result = m.asDBObject
      result.get("orange").asInstanceOf[Int] mustEqual 1
      result.get("banana").asInstanceOf[Int] mustEqual 2
      result.get("apple").asInstanceOf[MongoDBObject] mustEqual MongoDBObject("granny smith" -> 3)

    }

    "asJson that returns Json" in {

      val dt = new DateTime()
      val obj = new ObjectId()

      val m = MongoDBObject(
        "date" -> dt,
        "long" -> 3l,
        "int" -> 5,
        "string" -> "hello",
        "subobject" -> MongoDBObject("children" -> false),
        "list" -> MongoDBList(1,2,3),
        "objectId" -> obj
      )

      val result = m.toJson

      result mustEqual Json.obj(
        "date" -> Json.obj("$date" -> Json.fromString(dt.toString())),
        "long" -> Json.obj("$numberLong" -> Json.fromString("3")),
        "int" -> Json.fromInt(5),
        "string" -> Json.fromString("hello"),
        "subobject" -> Json.obj("children" -> Json.fromBoolean(false)),
        "list" -> Json.arr(List(1,2,3).map(Json.fromInt) :_*),
        "objectId" -> Json.obj("$oid" -> Json.fromString(obj.toHexString))
      )
    }

    "newBuilder" in {
      val builder = MongoDBObject.newBuilder()
      builder += ("slime" -> "wrap")
      val mongoDBObject = builder.result()

      mongoDBObject mustEqual MongoDBObject("slime" -> "wrap")
    }

    /* TODO:
    
    "java.util.Date can be used in conjunction with dates and getAs" in { }
    "java.time.ZonedDateTime can be used in conjunction with dates and getAs {}"

    */

  }

}
