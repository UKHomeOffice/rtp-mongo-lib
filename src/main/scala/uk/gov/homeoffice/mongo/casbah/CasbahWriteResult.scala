package uk.gov.homeoffice.mongo.casbah

import io.circe.Json
import org.bson.types.ObjectId

case class CasbahWriteResult(result :Json) {
  def getUpsertedId() :Option[ObjectId] = {
    result.hcursor.downField("getUpsertedId").downField("$oid").as[String].toOption match {
      case Some(id) => Some(new ObjectId(id))
      case None => result.hcursor.downField("getUpsertedId").as[String].toOption match {
        case Some(id) => Some(new ObjectId(id))
        case None => None
      }
    }
  }
  def wasAcknowledged() :Boolean = result.hcursor.downField("wasAcknowledged").as[Boolean].toOption.get
  def getMatchedCount() :Int = result.hcursor.downField("getMatchedCount").as[Int].toOption.get
  def getModifiedCount() :Int = result.hcursor.downField("getModifiedCount").as[Int].toOption.get
  def getN() :Int = result.hcursor.downField("getMatchedCount").as[Int].toOption.get
  def isUpdateOfExisting() :Boolean = (getMatchedCount() > 0)
}

