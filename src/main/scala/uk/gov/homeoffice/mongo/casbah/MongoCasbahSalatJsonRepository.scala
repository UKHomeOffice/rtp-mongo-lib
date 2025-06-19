package uk.gov.homeoffice.mongo.casbah

import io.circe.Json
import uk.gov.homeoffice.mongo.model.syntax._

abstract class MongoCasbahSalatJsonRepository[A](_mongoCasbahRepository :MongoCasbahRepository) extends MongoCasbahSalatRepository[A](_mongoCasbahRepository) {

  def toMongoObject(a :A) :MongoResult[MongoDBObject] = toJson(a).map { json => MongoDBObject(json) }
  def fromMongoObject(mongoDBObject :MongoDBObject) :MongoResult[A] = fromJson(mongoDBObject.toJson())

  def toJson(a :A) :MongoResult[Json]
  def fromJson(j :Json) :MongoResult[A]
}
