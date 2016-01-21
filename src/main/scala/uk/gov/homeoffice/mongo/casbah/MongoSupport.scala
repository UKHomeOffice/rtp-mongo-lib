package uk.gov.homeoffice.mongo.casbah

import com.mongodb.casbah.commons.conversions.scala.{RegisterConversionHelpers, RegisterJodaTimeConversionHelpers}

trait MongoSupport {
  RegisterConversionHelpers()
  RegisterJodaTimeConversionHelpers()
}