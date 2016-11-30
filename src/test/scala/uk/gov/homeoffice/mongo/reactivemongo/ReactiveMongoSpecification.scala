package uk.gov.homeoffice.mongo.reactivemongo

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag
import org.mockito.Answers._
import org.mockito.Mockito.withSettings
import org.specs2.mock.Mockito
import org.specs2.mutable.SpecificationLike
import reactivemongo.api._
import uk.gov.homeoffice.mongo.casbah.MongoSpecification

trait ReactiveMongoSpecification extends ReactiveMongo {
  spec: SpecificationLike with MongoSpecification =>

  isolated
  sequential

  lazy val driver = new MongoDriver

  def connection = driver.connection(List(mongoClient.address.toString))

  def reactiveMongoDB: DB with DBMetaCommands = connection.db(database)

  trait TestReactiveMongo extends ReactiveMongo {
    def reactiveMongoDB: DB with DBMetaCommands = spec.reactiveMongoDB
  }
}

trait MockReactiveMongo extends ReactiveMongo with Mockito {
  override def reactiveMongoDB: DB with DBMetaCommands = mock[DB with DBMetaCommands]

  def mockCollection[C <: Collection : ClassTag]: C = mock[C](withSettings.defaultAnswer(RETURNS_DEEP_STUBS.get))
}