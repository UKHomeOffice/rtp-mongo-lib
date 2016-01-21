package uk.gov.homeoffice.mongo.salat

import org.bson.types.ObjectId
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import com.novus.salat.global.ctx
import uk.gov.homeoffice.mongo.casbah.Mongo

abstract class Repository[M <: AnyRef](implicit m: Manifest[M]) extends ModelCompanion[M, ObjectId] with Mongo {
  val collection: String

  lazy val dao = new SalatDAO[M, ObjectId](db(collection)) {}
}