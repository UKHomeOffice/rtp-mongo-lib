package uk.gov.homeoffice.mongo.reactivemongo

import reactivemongo.api.DefaultDB

trait ReactiveMongo {
  def reactiveMongoDB: DefaultDB
}