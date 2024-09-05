package uk.gov.homeoffice.mongo.casbah

import io.circe.Json
import org.bson.types.ObjectId

case class CasbahInsertResult(result :Json) {
  def getInsertedId() :ObjectId = {
    result.hcursor.downField("getInsertedId").downField("$oid").as[String].toOption match {
      case Some(id) => new ObjectId(id)
      case None => throw new Exception(s"No insertedId for in CasbahResult: $result")
    }
  }
  def wasAcknowledged() :Boolean = result.hcursor.downField("wasAcknowledged").as[Boolean].toOption.get
}

