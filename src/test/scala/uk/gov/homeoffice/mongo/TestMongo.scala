package uk.gov.homeoffice.mongo

object TestMongo {
  def testConnection() :MongoConnection = {

    val randomDB = java.util.UUID.randomUUID().toString()
    println(s"Creating Test database connection to database $randomDB")

    val dbHost = scala.util.Try(sys.env("DB_TEST_HOST")).toOption.getOrElse("localhost")
    MongoConnector.connect(
      s"mongodb://$dbHost/$randomDB",
      "Test",
      false,
      randomDB
    )
  }

}
