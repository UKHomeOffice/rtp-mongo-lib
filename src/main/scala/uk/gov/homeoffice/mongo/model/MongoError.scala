package uk.gov.homeoffice.mongo.model

case class MongoError(message :String) {
  def prefix(prefix :String) :MongoError = MongoError(s"${prefix}: ${message}")
}
