package uk.gov.homeoffice.mongo.reactivemongo

import scala.concurrent.ExecutionContext.Implicits.global
import org.specs2.mutable.SpecificationLike
import reactivemongo.api.{DefaultDB, MongoDriver}
import uk.gov.homeoffice.mongo.casbah.MongoSpecification

trait ReactiveMongoSpecification extends ReactiveMongo {
  spec: SpecificationLike with MongoSpecification =>

  isolated
  sequential

  lazy val driver = new MongoDriver

  def connection = driver.connection(List(mongoClient.address.toString))

  def reactiveMongoDB = connection.db(database)

  trait TestReactiveMongo extends ReactiveMongo {
    def reactiveMongoDB: DefaultDB = spec.reactiveMongoDB
  }
}