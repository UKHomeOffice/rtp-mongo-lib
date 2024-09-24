package uk.gov.homeoffice.mongo.casbah

import io.circe.Json

case class CasbahDeleteResult(result :Json) {
  def getN() :Int = result.hcursor.downField("getN").as[Int].toOption.get
}

