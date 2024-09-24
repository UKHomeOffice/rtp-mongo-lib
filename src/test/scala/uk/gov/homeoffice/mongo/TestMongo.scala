package uk.gov.homeoffice.mongo

object TestMongo {
  def testConnection() :MongoConnection = {

    val randomDB = java.util.UUID.randomUUID().toString()
    println(s"Creating Test database connection to database $randomDB")

    MongoConnector.connect(
      s"mongodb://localhost/$randomDB",
      "Test",
      false,
      randomDB
    )
  }

}
