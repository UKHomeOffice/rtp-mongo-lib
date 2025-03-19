package uk.gov.homeoffice.mongo

import java.time._
import java.time.format.DateTimeFormatter

import org.joda.time.DateTime
import org.bson.types.ObjectId

import io.circe._
import scala.util.{Try, Success, Failure}

import cats.implicits.catsSyntaxOptionId

object MongoJsonEncoders {
  val dtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssX")
  val dtFormatter2: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX")

  object ApiEncoding {
    
    // java.time
    implicit val localDateEncoder: Encoder[LocalDate] = Encoder.encodeString.contramap[LocalDate](_.toString)
    implicit val localDateDecoder: Decoder[LocalDate] = Decoder.decodeString.emapTry { str =>
      Try(LocalDate.parse(str)) match {
        case Success(ld) => Success(ld)
        case Failure(error) => Try(LocalDate.parse(str.substring(0,10)))
      }
    }
    implicit val zonedDateTimeEncoder :Encoder[ZonedDateTime] = Encoder.encodeString.contramap[ZonedDateTime](dtFormatter.format)
    implicit val zonedDateTimeDecoder: Decoder[ZonedDateTime] = Decoder.decodeString.emapTry { str =>
      Try(ZonedDateTime.parse(str, dtFormatter)) match {
        case Success(zdt) => Success(zdt)
        case Failure(error) => Try(ZonedDateTime.parse(str, dtFormatter2))
      }
    }

    implicit val jodaDateTimeEncoder: Encoder[DateTime] = Encoder.encodeString.contramap[DateTime](_.toString)
    implicit val jodaDateTimeDecoder: Decoder[DateTime] = Decoder.decodeString.emapTry { str =>
      Try(DateTime.parse(str)) match {
        case Success(ld) => Success(ld)
        case Failure(error) => Try(DateTime.parse(str.substring(0,10)))
      }
    }

    // use the defaults, but provide them so callers don't need to
    // import auto to create a GenericEncoding instance
    implicit val numberLongEncoder :Encoder[Long] = Encoder.encodeLong
    implicit val numberLongDecoder :Decoder[Long] = Decoder.decodeLong

    implicit val numberIntEncoder :Encoder[Int] = Encoder.encodeInt
    implicit val numberIntDecoder :Decoder[Int] = Decoder.decodeInt

    // object id
    implicit val objectIdEncoder: Encoder[ObjectId] = Encoder.encodeString.contramap[ObjectId](_.toHexString)
    implicit val objectIdDecoder: Decoder[ObjectId] = Decoder.decodeString.emapTry { str =>
      Try(new ObjectId(str)) match {
        case Success(ld) => Success(ld)
        case Failure(error) => Try(new ObjectId(str))
      }
    }

  }

  object DatabaseEncoding {
    implicit val numberLongEncoder: Encoder[Long] = new Encoder[Long] {
        final def apply(l: Long): Json = Json.obj(
          "$numberLong" -> Json.fromString(l.toString)
        )
      }

    implicit val numberIntEncoder: Encoder[Int] = new Encoder[Int] {
        final def apply(l: Int): Json = Json.obj(
          "$numberInt" -> Json.fromString(l.toString)
        )
      }

    implicit val objectEncoder: Encoder[ObjectId] = new Encoder[ObjectId] {
        final def apply(l: ObjectId): Json = Json.obj("$oid" -> Json.fromString(l.toHexString))
      }

    // java.time
    implicit val localDateEncoder: Encoder[LocalDate] = new Encoder[LocalDate] {
        final def apply(ld: LocalDate): Json = Json.obj(
          ("$date", Json.obj(
              "$numberLong" -> Json.fromString(ld.atStartOfDay.toInstant(ZoneOffset.UTC).toEpochMilli.toString)
          ))
        )
      }

    implicit val localDateDecoder: Decoder[LocalDate] = new Decoder[LocalDate] {
      final def apply(c: HCursor): Decoder.Result[LocalDate] = {
        c.downField("$date").downField("$numberLong").as[String] match {
          case Left(decodeError) => Left(DecodingFailure(s"Unable to decode LocalDate: $c using Database Encoder: $decodeError", Nil))
          case Right(numberLong) => Right(LocalDate.ofInstant(Instant.ofEpochMilli(numberLong.toLong), ZoneOffset.UTC))
        }
      }
    }

    implicit val zonedDateTimeEncoder: Encoder[ZonedDateTime] = new Encoder[ZonedDateTime] {
      final def apply(zdt: ZonedDateTime): Json = Json.obj(
        ("$date", Json.obj(
            "$numberLong" -> Json.fromString(zdt.toInstant.toEpochMilli.toString)
        ))
      )
    }

    implicit val zonedDateTimeDecoder: Decoder[ZonedDateTime] = new Decoder[ZonedDateTime] {
      final def apply(c: HCursor): Decoder.Result[ZonedDateTime] = {
        c.downField("$date").downField("$numberLong").as[String] match {
          case Left(decodeError) => Left(DecodingFailure(s"Unable to decode ZonedDateTime: $c using Database Encoder", Nil))
          case Right(numberLong) => Right(ZonedDateTime.ofInstant(Instant.ofEpochMilli(numberLong.toLong), ZoneOffset.UTC).withNano(0))
        }
      }
    }

    // org.joda.time
    implicit val jodaDateTimeEncoder: Encoder[DateTime] = new Encoder[DateTime] {
        final def apply(ld: DateTime): Json = Json.obj(
          ("$date", Json.obj(
              "$numberLong" -> Json.fromString("123") //ld.atStartOfDay.toInstant(ZoneOffset.UTC).toEpochMilli.toString)
          ))
        )
      }

    implicit val jodaDateTimeDecoder: Decoder[DateTime] = new Decoder[DateTime] {
      final def apply(c: HCursor): Decoder.Result[DateTime] = {
        c.downField("$date").downField("$numberLong").as[String] match {
          case Left(decodeError) => Left(DecodingFailure(s"Unable to decode LocalDate: $c using Database Encoder: $decodeError", Nil))
          case Right(numberLong) => Right(DateTime.now) //Right(DateTime.ofInstant(Instant.ofEpochMilli(numberLong.toLong), ZoneOffset.UTC))
        }
      }
    }

    implicit val numberIntDecoder: Decoder[Int] = new Decoder[Int] {
      final def apply(c: HCursor): Decoder.Result[Int] = {
        c.downField("$numberInt").as[Int] match {
          case Left(decodeError) =>
            c.as[String] match {
              case Left(_) => Left(DecodingFailure(s"Unable to decode numberInt: $c using custom decoder ($decodeError)", Nil))
              case Right(plainInt) => Right(plainInt.toInt)
            }
          case Right(numberInt) => Right(numberInt)
        }
      }
    }

    implicit val numberLongDecoder: Decoder[Long] = new Decoder[Long] {
      final def apply(c: HCursor): Decoder.Result[Long] = {
        c.downField("$numberLong").as[Long] match {
          case Left(decodeError) =>
            c.as[String] match {
              case Left(_) => Left(DecodingFailure(s"Unable to decode numberLong: $c using custom decoder ($decodeError)", Nil))
              case Right(plainLong) => Right(plainLong.toLong)
            }
          case Right(numberLong) => Right(numberLong)
        }
      }
    }

    implicit val objectIdDecoder: Decoder[ObjectId] = new Decoder[ObjectId] {
      final def apply(c: HCursor): Decoder.Result[ObjectId] = {
        c.downField("$oid").as[String] match {
          case Left(decodeError) =>
            c.as[String] match {
              case Left(_) => Left(DecodingFailure(s"Unable to decode objectId: $c using custom decoder ($decodeError)", Nil))
              case Right(objectId) => Right(new ObjectId(objectId))
            }
          case Right(objectId) => Right(new ObjectId(objectId))
        }
      }
    }
  }

}

