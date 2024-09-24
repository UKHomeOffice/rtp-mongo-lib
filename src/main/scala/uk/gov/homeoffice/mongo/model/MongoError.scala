package uk.gov.homeoffice.mongo.model

import com.typesafe.scalalogging.Logger

case class MongoError(message :String) {

  val logger = Logger("MongoError")
  logger.info(s"MONGO ERROR RAISED: $message")

  def prefix(prefix :String) :MongoError = MongoError(s"${prefix}: ${message}")
}
