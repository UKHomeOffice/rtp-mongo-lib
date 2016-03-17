package uk.gov.homeoffice.mongo.casbah

import com.mongodb.DBObject
import com.mongodb.casbah.commons.MongoDBObject
import org.joda.time.DateTime

trait MongoObjectDate {
  def mongoObjectFromAndToDateOptions(fromDate: Option[DateTime], toDate: Option[DateTime]): Option[DBObject] = {
    val mongoObject = if (fromDate.isDefined) {
      if (toDate.isEmpty)
        Some(MongoDBObject("$gte" -> fromDate.get))
      else
        Some(MongoDBObject("$gte" -> fromDate.get, "$lte" -> toDate.get))
    } else if (toDate.isDefined) {
      Some(MongoDBObject("$lte" -> toDate.get))
    }
    else {
      None
    }

    mongoObject
  }
}