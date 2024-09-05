package uk.gov.homeoffice.mongo.casbah

import scala.collection._

import io.circe._
// This is a cheap clone of Casbah's MongoDBObject

import scala.util.Try
import org.bson.types.ObjectId
import org.joda.time.DateTime

class MongoDBObject(init :mutable.Map[String, AnyRef] = mutable.Map[String, AnyRef]()) {

  val data :mutable.Map[String, AnyRef] = init

  def expand(field :String, root :MongoDBObject) :Option[AnyRef] = {
    field.split("\\.").toList match {
      case Nil => None
      case head :: Nil => root.data.get(head)
      case head :: remaining =>
        root.data.get(head) match {
          case Some(v) if v.isInstanceOf[MongoDBObject] =>
            expand(remaining.mkString("."), v.asInstanceOf[MongoDBObject])
          case Some(v) => Some(v)
          case None => None
        }
    }
  }

  def as[A](field :String) :A =
    getAs[A](field).get

  def get(field :String) :Option[AnyRef] =
    getAs[AnyRef](field)

  def getAs[A](field :String) :Option[A] = {

    def lookahead(in :Any, field :String) :Option[AnyRef] =
      Try(in.asInstanceOf[MongoDBObject].get(field)).toOption.flatten

    val retval :Option[_] = expand(field, this).flatMap {
      case Some(i) => Some(i.asInstanceOf[AnyRef]) // TODO: consider if this is correct
      case oid :ObjectId => Some(oid)
      case m if lookahead(m, "$oid").isDefined => lookahead(m, "$oid").flatMap { anyRef =>
        Try(new ObjectId(anyRef.asInstanceOf[String])).toOption
      }
      case d if lookahead(d, "$date").isDefined => lookahead(d, "$date").flatMap {
        case l if l.isInstanceOf[Long] => Try(new DateTime(l.asInstanceOf[Long])).toOption
        case s => Try(DateTime.parse(s.asInstanceOf[String])).toOption
      }
      case m if lookahead(m, "$numberLong").isDefined => lookahead(m, "$numberLong").flatMap {
        case longStr => Try(longStr.asInstanceOf[String].toLong).toOption
      }
      case i if lookahead(i, "$numberInt").isDefined => lookahead(i, "$numberInt").flatMap {
        case intStr => Try(intStr.asInstanceOf[String].toInt).toOption
      }
      case f if lookahead(f, "$numberDouble").isDefined => lookahead(f, "$numberDouble").flatMap {
        case doubleStr => Try(doubleStr.asInstanceOf[String].toDouble).toOption
      }
      case v :Vector[_] => Some(MongoDBList(v.asInstanceOf[Vector[AnyRef]]))
      case x => Some(x)
    }

    retval match {
      case Some(v) if v.isInstanceOf[A] => Some(v.asInstanceOf[A])
      case _ => None
    }
  }

  def getAsOrElse[A](field :String, default :A) :A =
    getAs[A](field).getOrElse(default)

  def iterator() :Iterator[(String, AnyRef)] =
    data.iterator

  def ++(other :MongoDBObject) :MongoDBObject = {
    val dbObj = new MongoDBObject()
    data.foreach { case (k, v) => dbObj += (k -> v) }
    other.data.foreach { case (k, v) => dbObj += (k -> v) }
    dbObj
  }

  def +=[A](key :String, value :A) :MongoDBObject = {

    val writeValue :AnyRef = value match {
      case v if v.isInstanceOf[DateTime] => MongoDBObject("$date" -> value.asInstanceOf[DateTime].toString())
      case v if v.isInstanceOf[Long] => MongoDBObject("$numberLong" -> value.asInstanceOf[Long].toString())
      case v if v.isInstanceOf[ObjectId] => MongoDBObject("$oid" -> v.asInstanceOf[ObjectId].toHexString())
      case v => v.asInstanceOf[AnyRef]
    }

    key.split("\\.").toList match {
      case Nil => ()
      case head :: Nil => data += (key -> writeValue)
      case head :: remaining =>
        data.get(head) match {
          case Some(existingVal) if existingVal.isInstanceOf[MongoDBObject] =>
            // subobject is a map we can reuse.
            val child = existingVal.asInstanceOf[MongoDBObject]
            child += (remaining.mkString(".") -> value)
          case Some(existingVal) =>
            /* overwriting existingVal with new value */
            val child = new MongoDBObject()
            child += (remaining.mkString(".") -> value)
            data += (head -> child)
          case None =>
            val child = new MongoDBObject()
            child += (remaining.mkString(".") -> value)
            data += (head -> child)
        }
    }
    this
  }

  def +=[A](in :(String, A)) :MongoDBObject = {
    this += (in._1, in._2.asInstanceOf[AnyRef])
    this
  }

  def +=(other :MongoDBObject) :MongoDBObject = {
    other.data.toList.foreach { case (k, v) => this += (k -> v) }
    this
  }

  def -=(other :MongoDBObject) :Unit =
    other.data.keys.foreach { key => data.remove(key) }

  def removeField(field :String) :Unit = data.remove(field)

  def containsField(field :String) :Boolean =
    expand(field, this).isDefined

  def put[A](key :String, value :A) :MongoDBObject = {
    this += (key, value)
  }

  def put[A](in :(String, A)) :MongoDBObject = {
    this += (in._1, in._2)
  }

  def putAll(other :MongoDBObject) :MongoDBObject = {
    this += other
  }

  def keySet() :Set[String] = data.keySet
  def containsKey(key :String) :Boolean = data.keySet.contains(key)
  def toMap() :mutable.Map[String, AnyRef] = data /* Danger! Sharing a reference to a mutable object!! */

  def asDBObject() :DBObject = new DBObject(this)

  def toJson() :io.circe.Json = {
    import java.util.Date
    import java.time._

    def valueToJson(obj :AnyRef) :Json = {
      obj match {
        case s if s.isInstanceOf[String] => Json.fromString(s.asInstanceOf[String])
        case i if i.isInstanceOf[Int] => Json.fromInt(i.asInstanceOf[Int])
        case l if l.isInstanceOf[Long] => Json.obj("$numberLong" -> Json.fromString(l.asInstanceOf[Long].toString))
        case d if d.isInstanceOf[Double] => Json.fromDoubleOrNull(d.asInstanceOf[Double])
        case f if f.isInstanceOf[Float] => Json.fromFloatOrNull(f.asInstanceOf[Float])
        case b if b.isInstanceOf[BigDecimal] => Json.fromBigDecimal(b.asInstanceOf[BigDecimal])
        case l if l.isInstanceOf[Boolean] => Json.fromBoolean(l.asInstanceOf[Boolean])
        case d if d.isInstanceOf[java.util.Date] => Json.obj("$date" -> Json.fromString(d.toString())) // TODO: Correct ISO formatting
        case d if d.isInstanceOf[DateTime] => Json.obj("$date" -> Json.fromString(d.toString())) // TODO: Correct ISO formatting
        case m if m.isInstanceOf[MongoDBObject] => m.asInstanceOf[MongoDBObject].toJson()
        // TODO: Support java.time.ZonedDateTime + LocalDate
        case d if d.isInstanceOf[ObjectId] => Json.obj("$oid" -> Json.fromString(d.toString()))
        case l if l.isInstanceOf[MongoDBList[_]] =>
          val arr :List[AnyRef] = l.asInstanceOf[MongoDBList[AnyRef]].toList
          val jsonArr :List[Json] = arr.map { item :AnyRef => valueToJson(item) }
          Json.arr(jsonArr :_*)
        case l if l.isInstanceOf[Array[_]] =>
          val arr = l.asInstanceOf[Array[AnyRef]]
          val jsonArr = arr.map { item :AnyRef => valueToJson(item) }
          Json.arr(jsonArr :_*)
        case o if o.isInstanceOf[Map[_, _]] =>
          val inner = o.asInstanceOf[Map[String, AnyRef]]
          val keyPairs :List[(String, Json)] = inner.map { case (k, v) => (k -> valueToJson(v)) }.toList
          Json.obj(keyPairs :_*)
        case x => throw new Exception(s"MONGO EXCEPTION: primitive type stringified: ${Json.fromString(x.toString())}")
      }
    }

    val keys :List[(String, Json)] = data.map { case (k, v) => (k -> valueToJson(v)) }.toList

    Json.obj(keys :_*)
  }

  override def hashCode :Int = 41 * data.hashCode

  override def equals(other :Any) :Boolean = {
    other.isInstanceOf[MongoDBObject] match {
      case true =>
        data.equals(other.asInstanceOf[MongoDBObject].data)
      case false => false
    }
  }

  override def toString() :String = {
    val dataStr = data.map {
      case (k, v) if v.isInstanceOf[String] => s"$k -> ${v.asInstanceOf[String]}"
      case (k, v) if v.isInstanceOf[DateTime] => s"$k -> ${v.asInstanceOf[DateTime].toString}"
      case (k, v) if v.isInstanceOf[ObjectId] => s"$k -> ${v.asInstanceOf[ObjectId]}"
      case (k, v) if v.isInstanceOf[MongoDBObject] => s"$k -> ${v.asInstanceOf[MongoDBObject].toString}"
      case (k, v) if v.isInstanceOf[MongoDBList[_]] => s"$k -> ${v.asInstanceOf[MongoDBList[_]].toList.mkString(",")}"
      case (k, v) if v.isInstanceOf[mutable.Map[_, _]] => s"$k -> ${v.asInstanceOf[mutable.Map[String, AnyRef]].toString}"
      case (k, v) if v.isInstanceOf[AnyRef] => s"$k -> ${v}"
    }.mkString(",")

    s"MONGO DB OBJECT: $dataStr"
  }

  // Casbah users seem to confuse MongoDBObject and MongoDBObjectBuilder up
  // so for backwards compat this is just here, doing nothing
  def result() :MongoDBObject = this
}

object MongoDBObject {

  def empty() :MongoDBObject = new MongoDBObject()

  def newBuilder() :MongoDBObjectBuilder = new MongoDBObjectBuilder()

  def apply(json :io.circe.Json) :MongoDBObject = {

    // TODO, expand to support all expected types (e.g. bool, long etc)
    def lookahead(json :Json, fieldName :String) :Option[String] =
      json.hcursor.downField(fieldName).get[String](fieldName).toOption

    def jsonValueToObject(json :Json) :Option[AnyRef] = {
      json match {
        case j if j.isNumber => Some(j.asNumber.get.asInstanceOf[AnyRef])
        case j if j.isString => Some(j.asString.get.asInstanceOf[AnyRef])
        case a if a.isArray => Some(a.asArray.get.asInstanceOf[AnyRef])
        case n if n.isNull => None
        case b if b.isBoolean => Some(b.asBoolean.get.asInstanceOf[AnyRef])
        case m if m.isObject && lookahead(m, "$date").isDefined => lookahead(m, "$date").flatMap { str => scala.util.Try(DateTime.parse(str).asInstanceOf[AnyRef]).toOption }
        case m if m.isObject && lookahead(m, "$oid").isDefined => lookahead(m, "$oid").flatMap { str => scala.util.Try(new ObjectId(str).asInstanceOf[AnyRef]).toOption }
        case m if m.isObject && lookahead(m, "$numberLong").isDefined => lookahead(m, "$numberLong").flatMap { str => scala.util.Try(str.toLong.asInstanceOf[AnyRef]).toOption }
        case i if i.isObject && lookahead(i, "$numberInt").isDefined => lookahead(i, "$numberInt").flatMap { str => scala.util.Try(str.toInt.asInstanceOf[AnyRef]).toOption }
        case m if m.isObject =>
          val jsonList :List[(String, Json)] = m.asObject.get.toList
          val objList :List[(String, AnyRef)] = jsonList.map { case (key, jsVal) =>
            jsonValueToObject(jsVal) match {
              case None => None
              case Some(obj) => Some((key -> obj))
            }
          }.flatten

          val innerDBObject :MongoDBObject = MongoDBObject(objList :_*)
          Some(innerDBObject.asInstanceOf[AnyRef])
        case x => Some(x.toString.asInstanceOf[AnyRef])
      }
    }

    json.asObject match {
      case None => throw new Exception("MongoDBObject(json) where json is not a JsonObject")
      case Some(jsonObj) =>
        val objList :List[(String, AnyRef)] = jsonObj.toList.map { case (key, jsVal) =>
          jsonValueToObject(jsVal) match {
            case None => None
            case Some(obj) => Some((key -> obj))
          }
        }.flatten

        val map = mutable.Map[String, AnyRef](objList :_*)
        MongoDBObject(map)
    }
  }

  def apply(in :Map[String, AnyRef]) :MongoDBObject = {
    new MongoDBObject(mutable.Map(in.toList :_*))
  }

  def apply[A](in :(String, A)*) :MongoDBObject = {
    val dbObj = new MongoDBObject()
    in.foreach { case (key, value) =>
      value match {
        case obj => dbObj += (key, obj.asInstanceOf[AnyRef])
      }
    }
    dbObj
  }

  // An odd one as we've flipped the old relationship
  def apply(dbObject :DBObject) :MongoDBObject = {
    dbObject.mongoDBObject
  }

}
