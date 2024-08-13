package uk.gov.homeoffice.mongo.casbah

import io.circe.Json
import org.bson.types.ObjectId

// TODO: change as[A].toOption.get into something better, that wont throw Generic NotFound expections etc.
case class CasbahInsertResult(result :Json) {
  def getInsertedId() :ObjectId = new ObjectId(result.hcursor.downField("getInsertedId").as[String].toOption.get)
  def wasAcknowledged() :Boolean = result.hcursor.downField("wasAcknowledged").as[Boolean].toOption.get
}

