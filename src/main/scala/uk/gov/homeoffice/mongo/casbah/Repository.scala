package uk.gov.homeoffice.mongo.casbah

trait Repository extends Mongo {
  val collectionName: String

  lazy val collection = db(collectionName)

  def apply() = collection
}