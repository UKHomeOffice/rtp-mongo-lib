package uk.gov.homeoffice.mongo.casbah

import com.mongodb.casbah.{MongoClient, MongoClientURI, MongoDB}

trait Mongo {
  val mongoDB: MongoDB
}

/**
  * Mongo connection URIs which can be configured e.g. within the likes of reference.conf or application.conf, should use the standard Mongo URI connection scheme (allowing for single node or replica set).
  *
  * The following is the standard URI connection scheme:
  * mongodb://[username:password@]host1[:port1][,host2[:port2],...[,hostN[:portN]]][/[database][?options]]
  *
  * Example:
  * mongodb://user:homeoffice.gov.uk:27017,homeoffice.gov.uk:27017,homeoffice.gov.uk:27017/mydatabase
  */
object Mongo {
  val db: MongoClientURI => MongoDB =
    mongoClientURI => {
      val mongoClient = MongoClient(MongoClientURI(mongoClientURI.getURI))

      val database = mongoClientURI.database getOrElse { throw new Exception(s"No database configured to connect to ${mongoClientURI.hosts.mkString}") }

      mongoClient(database)
    }
}