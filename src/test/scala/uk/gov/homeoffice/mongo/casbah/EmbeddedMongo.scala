package uk.gov.homeoffice.mongo.casbah

import org.specs2.execute.{AsResult, Result}
import org.specs2.matcher.Scope
import grizzled.slf4j.Logging
import uk.gov.homeoffice.specs2.ComposableAround

/**
  * Mix in this trait (at example level) to provide a connection to an embedded Mongo for testing.
  * An embedded Mongo is started for each example.
  */
trait EmbeddedMongo extends Scope with ComposableAround with EmbeddedMongoExecutable with Logging {
  override def around[R: AsResult](r: => R): Result = try {
    startMongo()
    super.around(r)
  } finally {
    stopMongo()
  }
}