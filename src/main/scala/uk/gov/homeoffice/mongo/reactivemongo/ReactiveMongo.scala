package uk.gov.homeoffice.mongo.reactivemongo

import reactivemongo.api.{DB, DBMetaCommands}

trait ReactiveMongo {
  def reactiveMongoDB: DB with DBMetaCommands
}