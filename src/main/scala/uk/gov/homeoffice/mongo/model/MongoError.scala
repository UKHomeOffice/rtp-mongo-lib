package uk.gov.homeoffice.mongo.model

case class MongoError(message :String) {
  println(s"MONGO ERROR RAISED: $message")
  def prefix(prefix :String) :MongoError = MongoError(s"${prefix}: ${message}")
}
