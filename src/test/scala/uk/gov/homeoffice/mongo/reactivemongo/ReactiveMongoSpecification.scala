package uk.gov.homeoffice.mongo.reactivemongo

import java.util.concurrent.TimeUnit

import grizzled.slf4j.Logging
import org.mockito.Answers._
import org.mockito.Mockito.withSettings
import org.specs2.mock.Mockito
import org.specs2.mutable.SpecificationLike
import reactivemongo.api._
import uk.gov.homeoffice.mongo.casbah.MongoSpecification

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.FiniteDuration
import scala.reflect.ClassTag

trait ReactiveMongoSpecification extends ReactiveMongo with Logging {
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