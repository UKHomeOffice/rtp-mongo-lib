package uk.gov.homeoffice.mongo.casbah

import org.json4s.JValue
import org.json4s.JsonAST.{JInt, JObject, JString}
import org.specs2.matcher.Scope
import org.specs2.mutable.Specification
import uk.gov.homeoffice.json.JsonFormats

class EmbeddedMongoSpec extends Specification with EmbeddedMongoSpecification with MongoSupport with JsonFormats {
  trait Context extends Scope {
    val repository = (new Repository with TestMongo {
      val collectionName = "tests"
    })()
  }

  "Repository" should {
    "find nothing" in new Context {
      repository.find.toList must beEmpty
    }

    "save and find 1 test" in new Context {
      repository save JObject("key" -> JString("value"))

      val found: JValue = repository.find.toList.head
      (found \ "key").extract[String] mustEqual "value"
    }

    "save and find 2 tests" in new Context {
      val json1 = JObject("key" -> JInt(1))
      repository save json1

      val json2 = JObject("key" -> JInt(2))
      repository save json2

      val found1: JValue = repository.find.toList.head
      (found1 \ "key").extract[Int] mustEqual 1

      val found2: JValue = repository.find.toList(1)
      (found2 \ "key").extract[Int] mustEqual 2
    }
  }
}