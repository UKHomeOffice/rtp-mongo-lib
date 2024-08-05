package uk.gov.homeoffice.mongo

object model {

  case class MongoError(message :String)
  type MongoResult[A] = Either[MongoError, A]

}
