package uk.gov.homeoffice.mongo.reactivemongo

import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.Scope
import org.specs2.mutable.Specification
import reactivemongo.api.collections.default.BSONCollection
import reactivemongo.bson.BSONDocument
import uk.gov.homeoffice.mongo.casbah.EmbeddedMongoSpecification

class RepositorySpec(implicit ev: ExecutionEnv) extends Specification with EmbeddedMongoSpecification with ReactiveMongoSpecification {
  trait Context extends Scope {
    def usersCollection: BSONCollection = reactiveMongoDB.collection[BSONCollection]("users")
  }

  "Repository" should {
    "find nothing" in new Context {
      usersCollection.find(BSONDocument()).one[BSONDocument] must beLike[Option[BSONDocument]] {
        case None => ok
      }.await
    }

    "save and find 1 test" in new Context {
      usersCollection.save(BSONDocument("test" -> "testing")).flatMap { _ =>
        usersCollection.find(BSONDocument()).one[BSONDocument]
      } must beLike[Option[BSONDocument]] {
        case Some(_) => ok
      }.await
    }
  }
}