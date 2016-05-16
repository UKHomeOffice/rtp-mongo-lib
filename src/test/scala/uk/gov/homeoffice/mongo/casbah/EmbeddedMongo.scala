package uk.gov.homeoffice.mongo.casbah

import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.Scope
import grizzled.slf4j.Logging
import uk.gov.homeoffice.specs2.ComposableAround

/**
  * Mix in this trait (at example level) to provide a connection to an embedded Mongo for testing.
  * An embedded Mongo is started for each example.
  * There is an equivalent to this trait that is mixed in at specification level, which is preferred.
  * As Specs2 specifications (by default) are run in parallel, resources such as Mongo can be used up.
  * EmbeddedMongoSpecification runs all its examples sequentially, so is less resource intensive.
  */
trait EmbeddedMongo extends Scope with ComposableAround with EmbeddedMongoExecutable with Logging {
  startMongo()

  override def around[R: AsResult](r: => R): Result = try {
    super.around(r)
  } finally {
    stopMongo()
  }
}