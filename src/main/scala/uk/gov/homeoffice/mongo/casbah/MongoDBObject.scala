package uk.gov.homeoffice.mongo.casbah

import scala.collection._

import io.circe._

import scala.util.Try
import org.bson.types.ObjectId
import org.joda.time.DateTime
import scala.util.matching.Regex

/* This is a cheap clone of Casbah's MongoDBObject. It allows callers to port to the modern
 * driver without having to rewrite any database queries, hugely reducing effort. See the
 * README for gotcha involved in using this object such as how dotted notation is used.
*/

class MongoDBObject(init :mutable.Map[String, AnyRef] = mutable.Map[String, AnyRef]()) {

  val data :mutable.Map[String, AnyRef] = init

  def expand(field :String, root :MongoDBObject) :Option[AnyRef] =
    expandLiteralPath(field, root) match {
      case Some(v) => Some(v)
      case None => expandNestedPath(field, root)
    }

  def expandLiteralPath(field :String, root :MongoDBObject) :Option[AnyRef] = root.data.get(field)
  def expandNestedPath(field :String, root :MongoDBObject) :Option[AnyRef] = {
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

  def as[A](field :String)(implicit ev: reflect.ClassTag[A]) :A =
    getAs[A](field) match {
      case Some(a) => a
      case None =>
        throw new Exception(s"MongoDBObject.as for $field returned None (${asDBObject()})")
    }

  def get(field :String) :Option[AnyRef] =
    getAs[AnyRef](field)

  def getAs[A](field :String)(implicit ev: reflect.ClassTag[A]) :Option[A] = {

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

    val incomingTypeRequest = ev.runtimeClass.getName
    retval match {
      case Some(v) if incomingTypeRequest == "scala.math.BigDecimal" && v.isInstanceOf[Int] => Some(BigDecimal(v.asInstanceOf[Int].toString).asInstanceOf[A])
      case Some(v) if incomingTypeRequest == "scala.math.BigDecimal" && v.isInstanceOf[Long] => Some(BigDecimal(v.asInstanceOf[Long].toString).asInstanceOf[A])
      case Some(v) if incomingTypeRequest == "scala.math.BigDecimal" && v.isInstanceOf[Double] => Some(BigDecimal(v.asInstanceOf[Double].toString).asInstanceOf[A])
      case Some(v) if incomingTypeRequest == "scala.math.BigDecimal" && v.isInstanceOf[Float] => Some(BigDecimal(v.asInstanceOf[Float].toString).asInstanceOf[A])
      case Some(v) if incomingTypeRequest == "java.util.Date" && v.isInstanceOf[DateTime] => Some(v.asInstanceOf[DateTime].toDate().asInstanceOf[A])
      case Some(v) if incomingTypeRequest == "java.time.ZonedDateTime" && v.isInstanceOf[DateTime] => Some(java.time.ZonedDateTime.ofInstant(v.asInstanceOf[DateTime].toDate.toInstant, java.time.ZoneId.systemDefault()).asInstanceOf[A])
      case Some(v) => Some(v.asInstanceOf[A])
      case _ => None
    }
  }

  def getAsOrElse[A](field :String, default :A)(implicit ev: reflect.ClassTag[A]) :A =
    getAs[A](field).getOrElse(default)

  def iterator() :Iterator[(String, AnyRef)] =
    data.iterator

  def merge(other :MongoDBObject) :MongoDBObject = {
    val dbObj = new MongoDBObject()
    data.foreach { case (k, v) => dbObj += (k -> v) }
    other.data.foreach { case (k, v) =>
      data.get(k) match {
        case Some(existing) if existing.isInstanceOf[MongoDBObject] && v.isInstanceOf[MongoDBObject] =>
          val e = existing.asInstanceOf[MongoDBObject]
          dbObj += (k -> e.merge(v.asInstanceOf[MongoDBObject]).asInstanceOf[AnyRef])
        case _ => dbObj += (k -> v)
      }
    }
    dbObj
  }

  def ++(other :MongoDBObject) :MongoDBObject = {
    val dbObj = new MongoDBObject()
    data.foreach { case (k, v) => dbObj += (k -> v) }
    other.data.foreach { case (k, v) => dbObj += (k -> v) }
    dbObj
  }

  def +=[A](key :String, value :A) :MongoDBObject = {
    if (value == null) return this

    data += (key -> value.asInstanceOf[AnyRef])
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
        case d if d.isInstanceOf[Regex] => Json.obj("$regex" -> Json.fromString(d.asInstanceOf[Regex].pattern.pattern)) // TODO: Set pattern
        case m if m.isInstanceOf[MongoDBObject] => m.asInstanceOf[MongoDBObject].toJson()
        case m if m.isInstanceOf[DBObject] => m.asInstanceOf[DBObject].mongoDBObject.toJson()
        case d if d.isInstanceOf[ObjectId] => Json.obj("$oid" -> Json.fromString(d.toString()))
        case l if l.isInstanceOf[MongoDBList[_]] =>
          val arr :List[AnyRef] = l.asInstanceOf[MongoDBList[AnyRef]].toList()
          val jsonArr :List[Json] = arr.map { (item :AnyRef) => valueToJson(item) }
          Json.arr(jsonArr :_*)
        // handle all collections
        case l if l.isInstanceOf[Array[_]] =>
          val arr = l.asInstanceOf[Array[AnyRef]]
          val jsonArr = arr.map { (item :AnyRef) => valueToJson(item) }
          Json.arr(jsonArr :_*)
        case l if l.isInstanceOf[List[_]] =>
          val arr = l.asInstanceOf[List[AnyRef]]
          val jsonArr = arr.map { (item :AnyRef) => valueToJson(item) }
          Json.arr(jsonArr :_*)
        case l if l.isInstanceOf[Seq[_]] =>
          val arr = l.asInstanceOf[Seq[AnyRef]]
          val jsonArr = arr.map { (item :AnyRef) => valueToJson(item) }.toList
          Json.arr(jsonArr :_*)
        case l if l.isInstanceOf[Set[_]] =>
          val arr = l.asInstanceOf[Set[AnyRef]]
          val jsonArr = arr.map { (item :AnyRef) => valueToJson(item) }.toList
          Json.arr(jsonArr :_*)
        case o if o.isInstanceOf[Map[_, _]] =>
          val inner = o.asInstanceOf[Map[String, AnyRef]]
          val keyPairs :List[(String, Json)] = inner.map { case (k, v) => (k -> valueToJson(v)) }.toList
          Json.obj(keyPairs :_*)
        case s if s.isInstanceOf[Some[_]] =>
          valueToJson(s.asInstanceOf[Some[AnyRef]].get)
        case n if n.isInstanceOf[Option[_]] && n.asInstanceOf[Option[_]].isEmpty =>
          Json.Null
        case x => if (x == null) {
            null
          } else {
            throw new Exception(s"MONGO EXCEPTION: primitive type stringified: $x ($data)")
          }
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
      case (k, v) if v == null => s"$k -> NULL"
      case (k, v) if v.isInstanceOf[String] => s"$k -> ${v.asInstanceOf[String]}"
      case (k, v) if v.isInstanceOf[DateTime] => s"$k -> ${v.asInstanceOf[DateTime].toString}"
      case (k, v) if v.isInstanceOf[ObjectId] => s"$k -> ${v.asInstanceOf[ObjectId]}"
      case (k, v) if v.isInstanceOf[MongoDBObject] => s"$k -> ${v.asInstanceOf[MongoDBObject].toString}"
      case (k, v) if v.isInstanceOf[MongoDBList[_]] => s"$k -> ${v.asInstanceOf[MongoDBList[_]].toList().mkString(",")}"
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
        case a if a.isArray =>
          val array = a.asArray.get.asInstanceOf[Vector[Any]]
          val mongoDBObjectArray :Vector[AnyRef] = array.map { j => j match {
            case o if o.isInstanceOf[Json] => jsonValueToObject(o.asInstanceOf[Json]) match {
              case Some(v) => v
              case None => o.asInstanceOf[Any].asInstanceOf[AnyRef]
            }
            case r => r.asInstanceOf[AnyRef]
          }}
          Some(mongoDBObjectArray.asInstanceOf[Vector[AnyRef]])
        case n if n.isNull => None
        case b if b.isBoolean => Some(b.asBoolean.get.asInstanceOf[AnyRef])
        case m if m.isObject && lookahead(m, "$date").isDefined => lookahead(m, "$date").flatMap { str => scala.util.Try(DateTime.parse(str).asInstanceOf[AnyRef]).toOption }
        case m if m.isObject && lookahead(m, "$oid").isDefined => lookahead(m, "$oid").flatMap { str => scala.util.Try(new ObjectId(str).asInstanceOf[AnyRef]).toOption }
        case m if m.isObject && lookahead(m, "$numberLong").isDefined => lookahead(m, "$numberLong").flatMap { str => scala.util.Try(str.toLong.asInstanceOf[AnyRef]).toOption }
        case i if i.isObject && lookahead(i, "$numberInt").isDefined => lookahead(i, "$numberInt").flatMap { str => scala.util.Try(str.toInt.asInstanceOf[AnyRef]).toOption }
        case m if m.isObject =>
          val jsonList :List[(String, Json)] = m.asObject.get.toList
          val objList :List[(String, AnyRef)] = jsonList.flatMap { case (key, jsVal) =>
            jsonValueToObject(jsVal) match {
              case None => None
              case Some(obj) => Some((key -> obj))
            }
          }

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

  def apply(dbObject :DBObject) :MongoDBObject = {
    dbObject.mongoDBObject
  }

}
