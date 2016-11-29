package uk.gov.homeoffice.mongo.reactivemongo

import scala.concurrent.ExecutionContext.Implicits.global
import org.specs2.mock.Mockito
import org.specs2.mutable.SpecificationLike
import reactivemongo.api.{DB, MongoDriver}
import uk.gov.homeoffice.mongo.casbah.MongoSpecification

trait ReactiveMongoSpecification extends ReactiveMongo {
  spec: SpecificationLike with MongoSpecification =>

  isolated
  sequential

  lazy val driver = new MongoDriver

  def connection = driver.connection(List(mongoClient.address.toString))

  def reactiveMongoDB: DB = connection.db(database)

  trait TestReactiveMongo extends ReactiveMongo {
    def reactiveMongoDB: DB = spec.reactiveMongoDB
  }
}

trait MockReactiveMongo extends ReactiveMongo with Mockito {
  def reactiveMongoDB: DB = mock[DB]
}
