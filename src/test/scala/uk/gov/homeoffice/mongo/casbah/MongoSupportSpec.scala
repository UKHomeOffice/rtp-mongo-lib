package uk.gov.homeoffice.mongo.casbah

import com.github.limansky.mongoquery.casbah._
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.specs2.mutable.Specification

class MongoSupportSpec extends Specification with MongoSupport {
  val `yyyy-MM-dd` = DateTimeFormat forPattern "yyyy-MM-dd"

  "Date range query" should {
    "be empty" in {
      dateRangeQuery() must beNone
    }

    "be equal to or greater than 'from' date" in {
      val fromDate = DateTime.parse("2001-01-01", `yyyy-MM-dd`)

      dateRangeQuery(from = Some(fromDate)) mustEqual Some(mq"{ $$gte: $fromDate }")
    }

    "be less than or equal to 'to' date" in {
      val toDate = DateTime.parse("2009-01-01", `yyyy-MM-dd`)

      dateRangeQuery(to = Some(toDate)) mustEqual Some(mq"{ $$lte: $toDate }")
    }

    "be equal to or greater than 'from' date and be less than or equal to 'to' date" in {
      val fromDate = DateTime.parse("2001-01-01", `yyyy-MM-dd`)
      val toDate = DateTime.parse("2009-01-01", `yyyy-MM-dd`)

      dateRangeQuery(Some(fromDate), Some(toDate)) mustEqual Some(mq"{ $$gte: $fromDate, $$lte: $toDate }")
    }
  }
}