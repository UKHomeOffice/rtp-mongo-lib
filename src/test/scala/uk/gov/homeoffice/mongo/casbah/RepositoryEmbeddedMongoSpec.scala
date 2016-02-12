package uk.gov.homeoffice.mongo.casbah

import org.json4s.JsonAST.{JInt, JObject, JString}
import org.specs2.matcher.Scope
import org.specs2.mutable.Specification
import uk.gov.homeoffice.json.JsonFormats

class RepositoryEmbeddedMongoSpec extends Specification with EmbeddedMongoSpec with MongoSupport with JsonFormats {
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

      repository.find.toList must beLike {
        case List(dbObject) => (toJson(dbObject) \ "key").extract[String] mustEqual "value"
      }
    }

    "save and find 2 tests" in new Context {
      val testModel1 = JObject("key" -> JInt(1))
      repository save testModel1

      val testModel2 = JObject("key" -> JInt(2))
      repository save testModel2

      repository.find.toList must beLike {
        case List(dbObject1, dbObject2) =>
          (toJson(dbObject1) \ "key").extract[Int] mustEqual 1
          (toJson(dbObject2) \ "key").extract[Int] mustEqual 2
      }
    }
  }
}