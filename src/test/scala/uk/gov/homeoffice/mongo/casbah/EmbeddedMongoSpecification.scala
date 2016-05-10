package uk.gov.homeoffice.mongo.casbah

import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.SpecificationLike
import org.specs2.specification.AroundEach

/**
 * Mix in this trait (at specification level) to provide a connection to an embedded Mongo for testing.
 * Every running example will be given its own unique instance of Mongo.
 * If you can, it is recommended to use EmbeddedMongo instead, as it is mixed in at the example level and can aid code readability.
 */
trait EmbeddedMongoSpecification extends AroundEach with Mongo with EmbeddedMongoExecutable {
  spec: SpecificationLike =>

  isolated

  override def around[R: AsResult](r: => R): Result = try {
    startMongo()
    AsResult(r)
  } finally {
    stopMongo()
  }
}