package uk.gov.homeoffice.mongo.salat

import org.bson.types.ObjectId
import com.novus.salat.Context
import com.novus.salat.dao.{SalatDAO, ModelCompanion}
import uk.gov.homeoffice.mongo.casbah.Mongo

abstract class Repository[M <: AnyRef with Product](implicit c: Context = com.novus.salat.global.ctx, m: Manifest[M]) extends ModelCompanion[M, ObjectId] with Mongo {
  val collectionName: String

  lazy val dao = new SalatDAO[M, ObjectId](db(collectionName)) {}
}