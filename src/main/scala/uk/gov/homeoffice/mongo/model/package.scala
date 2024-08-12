package uk.gov.homeoffice.mongo

object model {

  case class MongoError(message :String)
  type MongoResult[A] = Either[MongoError, A]

  // TODO: class MongoInsertException for failures during INSERT (to help locking repo catch errors appropriately?)
  class MongoException(msg :String) extends Exception(msg)

}
