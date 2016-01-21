package uk.gov.homeoffice.mongo.salat

import org.specs2.matcher.Scope
import org.specs2.mutable.Specification
import uk.gov.homeoffice.mongo.casbah.MongoSpec

class RepositorySpec extends Specification with MongoSpec {
  trait Context extends Scope {
    val repository = new Repository[Test] with TestMongo {
      val collection = "tests"
    }
  }

  "Repository" should {
    "find nothing" in new Context {
      repository.findAll().toList must beEmpty
    }

    "save and find 1 test" in new Context {
      val test = Test("1")
      repository save test

      repository.findAll().toList must beLike {
        case List(Test(id)) => id mustEqual test.id
      }
    }

    "save and find 2 tests" in new Context {
      val test1 = Test("1")
      repository save test1

      val test2 = Test("2")
      repository save test2

      repository.findAll().toList must beLike {
        case List(Test(id1), Test(id2)) =>
          id1 mustEqual test1.id
          id2 mustEqual test2.id
      }
    }
  }
}

case class Test(id: String)