package uk.gov.homeoffice.mongo.casbah

/**
  * Example usage:
  * <pre>
  *   trait ThingsRepository extends Repository {
  *     val collectionName = "things"
  *   }
  *
  *   val thingsRepository = new ThingsRepository with MyMongo
  *   thingsRepository.collection.save(<my JSON>)
  *
  *   // OR if you "apply" the instance of the repository being created (a la JavaScript), then you don't need to call "collection" before calling an API method such as "save", "find" etc.
  *
  *   val thingsRepository = (new ThingsRepository with MyMongo)()
  *   thingsRepository.save(<my JSON>)
  * </pre>
  *
  * Note that you have to provide a Casbah MongoDB as declared by the Mongo trait e.g.
  * <pre>
  *   trait MyMongo extends Mongo {
  *     lazy val db = MyMongo.mydb
  *   }
  *
  *   object MyMongo {
  *     lazy val mydb = Mongo db MongoClientURI(ConfigFactory.load getString "mydb")
  *   }
  * </pre>
  */
trait Repository extends Mongo {
  val collectionName: String

  lazy val collection = mongoDB(collectionName)

  def apply() = collection
}