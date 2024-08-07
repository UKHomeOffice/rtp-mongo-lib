package uk.gov.homeoffice.mongo.casbah

import scala.collection._

import io.circe._
// This is a cheap clone of Casbah's MongoDBObject

class MongoDBObject(init :mutable.Map[String, Object] = mutable.Map[String, Object]()) {
  
  val data :mutable.Map[String, Object] = init

  def expand(field :String, root :mutable.Map[String, Object]) :Option[Object] = {
    field.split(".").toList match {
      case Nil => None
      case head :: Nil => root.get(head)
      case head :: remaining => expand(remaining.mkString("."), root.get(head).asInstanceOf[mutable.Map[String,Object]])
    }
  }

  def as[A](field :String) :A =
    expand(field, data).map(_.asInstanceOf[A]).get // (intentionally) throws .get was empty exception

  def get(field :String) :Object =
    expand(field, data).getOrElse(new Object()) // wtf

  def getAs[A](field :String) :Option[A] =
    expand(field, data).map(_.asInstanceOf[A])

  def getAsOrElse[A](field :String, default :() => A) :A =
    expand(field, data).map(_.asInstanceOf[A]).getOrElse(default())

  def iterator() :Iterator[(String, Object)] =
    data.iterator

  def ++(other :MongoDBObject) :MongoDBObject = {
    val combined :Map[String, Object] = data ++ other.data
    val dbObj = new MongoDBObject()
    dbObj += combined
    dbObj
  }

  def +=[A](key :String, value :A) :MongoDBObject = {
    data += (key -> value.asInstanceOf[Object])
    this
  }

  def +=[A](in :(String, A)) :MongoDBObject = {
    data += (in._1 -> in._2.asInstanceOf[Object])
    this
  }

  def +=(other :MongoDBObject) :MongoDBObject = {
    other.data.toList.foreach { case (k, v) => data += (k -> v) }
    this
  }

  def +=(otherMap :Map[String, Object]) :MongoDBObject = {
    otherMap.toList.foreach { case (k, v) => data += (k -> v) }
    this
  }

  def -=(other :MongoDBObject) :MongoDBObject = ???

  def containsField(field :String) :Boolean =
    expand(field, data).isDefined

  def put[A](key :String, value :A) :MongoDBObject = {
    data += (key -> value.asInstanceOf[Object])
    this
  }

  def put[A](in :(String, A)) :MongoDBObject = {
    data += (in._1 -> in._2.asInstanceOf[Object])
    this
  }

  def putAll(other :MongoDBObject) :MongoDBObject = {
    this += other
  }

  def asDBObject() :Object = throw new Exception("asDBObject not implemented")

  def toJson() :io.circe.Json = {
    import java.util.Date
    import java.time._

    def valueToJson(obj :Object) :Json = {
      obj match {
        case s if s.isInstanceOf[String] => Json.fromString(s.asInstanceOf[String])
        case i if i.isInstanceOf[Int] => Json.fromInt(i.asInstanceOf[Int])
        case l if l.isInstanceOf[Long] => Json.fromLong(l.asInstanceOf[Long])
        case l if l.isInstanceOf[Boolean] => Json.fromBoolean(l.asInstanceOf[Boolean])
        case d if d.isInstanceOf[java.util.Date] => Json.obj("$date" -> Json.fromString(d.toString())) // TODO: Date format...
        /* case d if d.isInstanceOf[ObjectId] => (k -> Json.obj("$oid" -> Json.fromString(d.toString()))) ObjectId -> Json. */
        /* case d if d.isInstanceOf[ObjectId] => (k -> Json.obj("$oid" -> Json.fromString(d.toString()))) ObjectId -> Json. */
        case l if l.isInstanceOf[Array[_]] =>
          val arr = l.asInstanceOf[Array[Object]]
          val jsonArr = arr.map { item :Object => valueToJson(item) }
          Json.arr(jsonArr :_*)
        case o if o.isInstanceOf[Map[String, Object]] =>
          val inner = o.asInstanceOf[Map[String, Object]]
          val keys :List[(String, Json)] = data.map { case (k, v) => (k -> valueToJson(v)) }.toList
          Json.obj(keys :_*)
        case x => Json.fromString(x.toString())
      }
    }

    val keys :List[(String, Json)] = data.map { case (k, v) => (k -> valueToJson(v)) }.toList

    Json.obj(keys :_*)
  }
}

/*
case class MongoDBObjectBuilder(obj :MongoDBObject) {
  def +=(items :(String, Object)*) = obj += items.toMap
  def +=(other :MongoDBObject) = obj += other.data
  def put(items :(String, Object)*) = this += items
  def putAll(other :MongoDBObject) = this.put(other.data.toList)
  def result() :MongoDBObject = obj
}*/

case class MongoDBList(array :Array[Object])

object MongoDBObject {

  def empty() :MongoDBObject = new MongoDBObject()

  //def newBuilder() :MongoDBObjectBuilder = new MongoDBObjectBuilder(new MongoDBObject)

  def apply(json :io.circe.Json) :MongoDBObject = {

    def jsonValueToObject(json :Json) :Option[Object] = {
      json match {
        case j if j.isNumber => Some(j.asNumber.get.asInstanceOf[Object])
        case j if j.isString => Some(j.asString.get.asInstanceOf[Object])
        case a if a.isArray => Some(a.asArray.get.asInstanceOf[Object])
        case n if n.isNull => None
        case b if b.isBoolean => Some(b.asBoolean.get.asInstanceOf[Object])
        case m if m.isObject =>
          val jsonList :List[(String, Json)] = m.asObject.get.toList
          val objList :List[(String, Object)] = jsonList.map { case (key, jsVal) =>
            jsonValueToObject(jsVal) match {
              case None => None
              case Some(obj) => Some((key -> obj))
            }
          }.flatten

          val map :Map[String, Object] = objList.toMap
          Some(map.asInstanceOf[Object])
        case x => Some(x.toString.asInstanceOf[Object])
      }
    }

    json.asObject match {
      case None => throw new Exception("MongoDBObject(json) where json is not a JsonObject")
      case Some(jsonObj) =>
        val objList :List[(String, Object)] = jsonObj.toList.map { case (key, jsVal) =>
          jsonValueToObject(jsVal) match {
            case None => None
            case Some(obj) => Some((key -> obj))
          }
        }.flatten

        val map :Map[String, Object] = objList.toMap

        MongoDBObject(map)
    }
  }

  def apply(in :Map[String, Object]) :MongoDBObject = {
    new MongoDBObject(mutable.Map(in.toList :_*))
  }

  def apply(in :(String, Object)*) :MongoDBObject = {
    val dbObj = new MongoDBObject()
    in.foreach { case (key, value) =>
      value match {
        case dt if dt.isInstanceOf[java.util.Date] => dbObj += (key, Map[String, Object]("$date" -> dt.toString))
        case obj => dbObj += (key, obj)
      }
    }
    dbObj
  }

}
