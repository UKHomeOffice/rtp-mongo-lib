package uk.gov.homeoffice.mongo.casbah

import scala.collection._

import io.circe._
// This is a cheap clone of Casbah's MongoDBObject

import org.bson.types.ObjectId
import org.joda.time.DateTime

class MongoDBObject(init :mutable.Map[String, AnyRef] = mutable.Map[String, AnyRef]()) {
  
  val data :mutable.Map[String, AnyRef] = init

  def expand(field :String, root :mutable.Map[String, AnyRef]) :Option[AnyRef] = {
    println(s"EXPAND looking for $field in ${root.keys}")
    field.split("\\.").toList match {
      case Nil => None
      case head :: Nil => root.get(head)
      case head :: remaining => expand(remaining.mkString("."), root.get(head).asInstanceOf[mutable.Map[String,AnyRef]])
    }
  }

  def as[A](field :String) :A =
    getAs[A](field).get

  def get(field :String) :Option[AnyRef] =
    getAs[AnyRef](field)

  def getAs[A](field :String) :Option[A] = {
    def lookahead(in :Object, field :String) :Option[AnyRef] = in.isInstanceOf[Map[_, _]] match {
      case true =>
        val innerMap = in.asInstanceOf[Map[String, AnyRef]]
        (innerMap.keySet.toList == List(field)) match {
          case true => innerMap.get(field)
          case false => None
        }
      case false => None
    }

    expand(field, data).map {
      case s :Some[_] => s.asInstanceOf[Some[AnyRef]].get.asInstanceOf[A]
      case oid :ObjectId => oid.asInstanceOf[A]
      /* warning, can throw NoneException */
      case m if lookahead(m, "$oid").isDefined => lookahead(m, "$oid").map( anyRef => new ObjectId(anyRef.asInstanceOf[String])).get.asInstanceOf[A]
      case m if lookahead(m, "$date").isDefined => lookahead(m, "$date").map { innerValue => innerValue match {
        case x if x.isInstanceOf[String] => DateTime.parse(innerValue.asInstanceOf[String]).asInstanceOf[A]
        case j if lookahead(j, "$numberLong").isDefined => lookahead(j, "$numberLong").map { innerValue => new DateTime(innerValue.asInstanceOf[String].toLong) }.get.asInstanceOf[A]
      }}.get.asInstanceOf[A]
      case m if lookahead(m, "$numberLong").isDefined => lookahead(m, "$numberLong").map { longStr => longStr.asInstanceOf[String].toLong }.get.asInstanceOf[A]
      case v :Vector[_] =>
        println(s"In the vector branch with $v ${v.length}")
        MongoDBList(v.asInstanceOf[Vector[AnyRef]]).asInstanceOf[A]
      case x => x.asInstanceOf[A]
    }
  }

  def getAsOrElse[A](field :String, default :() => A) :A =
    getAs[A](field).getOrElse(default())

  def iterator() :Iterator[(String, AnyRef)] =
    data.iterator

  def ++(other :MongoDBObject) :MongoDBObject = {
    val combined :Map[String, AnyRef] = data ++ other.data
    val dbObj = new MongoDBObject()
    dbObj += combined
    dbObj
  }

  def +=[A](key :String, value :A) :MongoDBObject = {
    println(s"Adding $key to MongoDBObject. $value (${value.getClass})")
    value match {
      case v :Option[_] if v.asInstanceOf[Option[AnyRef]].isDefined => this += (key, v.asInstanceOf[Option[AnyRef]].get)
      case v :Some[_] if v.asInstanceOf[Some[AnyRef]].isDefined => this += (key, v.asInstanceOf[Some[AnyRef]].get)
      case x =>
        data += (key -> x.asInstanceOf[AnyRef])
        this
    }
  }

  def +=[A](in :(String, A)) :MongoDBObject = {
    this += (in._1, in._2.asInstanceOf[AnyRef])
    this
  }

  def +=(other :MongoDBObject) :MongoDBObject = {
    other.data.toList.foreach { case (k, v) => this += (k -> v) }
    this
  }

  def +=(otherMap :Map[String, AnyRef]) :MongoDBObject = {
    otherMap.toList.foreach { case (k, v) => this += (k -> v) }
    this
  }

  def -=(other :MongoDBObject) :Unit =
    other.data.keys.foreach { key => data.remove(key) }

  def removeField(field :String) :Unit = data.remove(field)

  def containsField(field :String) :Boolean =
    expand(field, data).isDefined

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
  def toMap() :Map[String, AnyRef] = data /* Danger! Sharing a reference to a mutable object!! */

  def asDBObject() :DBObject = new DBObject(this)

  def toJson() :io.circe.Json = {
    import java.util.Date
    import java.time._

    def valueToJson(obj :AnyRef) :Json = {
      obj match {
        case s if s.isInstanceOf[String] => Json.fromString(s.asInstanceOf[String])
        case i if i.isInstanceOf[Int] => Json.fromInt(i.asInstanceOf[Int])
        case l if l.isInstanceOf[Long] => Json.obj("$numberLong" -> Json.fromString(l.asInstanceOf[Long].toString))
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
        case x => Json.fromString(x.toString())
      }
    }

    val keys :List[(String, Json)] = data.map { case (k, v) => (k -> valueToJson(v)) }.toList

    Json.obj(keys :_*)
  }

  override def toString() :String = {
    val dataStr = data.map {
      case (k, v) if v.isInstanceOf[String] => s"$k -> ${v.asInstanceOf[String]}"
      case (k, v) if v.isInstanceOf[DateTime] => s"$k -> ${v.asInstanceOf[DateTime].toString}"
      case (k, v) if v.isInstanceOf[ObjectId] => s"$k -> ${v.asInstanceOf[ObjectId]}"
      case (k, v) if v.isInstanceOf[MongoDBObject] => s"$k -> ${v.asInstanceOf[MongoDBObject].toString}"
      case (k, v) if v.isInstanceOf[MongoDBList[_]] => s"$k -> ${v.asInstanceOf[MongoDBList[_]].toList.mkString(",")}"
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
        case m if m.isObject =>
          val jsonList :List[(String, Json)] = m.asObject.get.toList
          val objList :List[(String, AnyRef)] = jsonList.map { case (key, jsVal) =>
            jsonValueToObject(jsVal) match {
              case None => None
              case Some(obj) => Some((key -> obj))
            }
          }.flatten

          val map :Map[String, AnyRef] = objList.toMap
          Some(map.asInstanceOf[AnyRef])
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

        val map :Map[String, AnyRef] = objList.toMap
        println(s"JSON REINFLATE TURNS $json INTO $map")
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
        //case dt if dt.isInstanceOf[java.util.Date] => dbObj += (key, Map[String, AnyRef]("$date" -> dt.toString))
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
