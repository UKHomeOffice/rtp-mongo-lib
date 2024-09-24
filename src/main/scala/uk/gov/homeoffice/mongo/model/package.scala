package uk.gov.homeoffice.mongo.model

object syntax {
  type MongoResult[A] = Either[MongoError, A]
}
