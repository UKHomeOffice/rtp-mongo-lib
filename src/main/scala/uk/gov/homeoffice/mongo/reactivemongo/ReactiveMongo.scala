package uk.gov.homeoffice.mongo.reactivemongo

import reactivemongo.api.DefaultDB

trait ReactiveMongo {
  val reactiveMongoDB: DefaultDB
}