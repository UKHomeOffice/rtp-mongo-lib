package uk.gov.homeoffice.mongo.salat

import org.bson.types.ObjectId
import salat.Context
import salat.dao.{ModelCompanion, SalatDAO}
import uk.gov.homeoffice.mongo.casbah.Mongo

/**
  * Example usage:
  * <pre>
  *   trait ThingsRepository extends Repository[Thing] {
  *     val collectionName = "things"
  *   }
  *
  *   val thingsRepository = new ThingsRepository with MyMongo
  *   thingsRepository save Thing()
  * </pre>
  *
  * When extending this Repository, you can provide your own context by overridding "context" which defaults to the one provided by Salat.
  *
  * Note that you have to provide a Casbah MongoDB as declared by the Mongo trait e.g.
  * <pre>
  *   trait MyMongo extends Mongo {
  *     lazy val db = MyMongo.mydb
  *   }
  *
  *   object MyMongo {
  *     lazy val mydb = Mongo db MongoClientURI(ConfigFactory.load getString "mydb")
  *   }
  * </pre>
  */
abstract class Repository[M <: AnyRef with Product](implicit m: Manifest[M]) extends ModelCompanion[M, ObjectId] with Mongo {
  com.mongodb.casbah.commons.conversions.scala.RegisterJodaTimeConversionHelpers()

  val collectionName: String

  implicit val context: Context = salat.global.ctx

  lazy val dao = new SalatDAO[M, ObjectId](mongoDB(collectionName)) {}
}