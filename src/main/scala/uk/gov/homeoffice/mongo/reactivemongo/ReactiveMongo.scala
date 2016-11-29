package uk.gov.homeoffice.mongo.reactivemongo

import reactivemongo.api.DB

trait ReactiveMongo {
  def reactiveMongoDB: DB
}