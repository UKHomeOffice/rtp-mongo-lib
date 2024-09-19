package uk.gov.homeoffice.mongo.casbah

import scala.jdk.CollectionConverters._

import org.bson.types.ObjectId
import org.joda.time.DateTime

class DBObject(val mongoDBObject :MongoDBObject) {

  def containsField(key :String) = mongoDBObject.containsField(key)
  def containsKey(key :String) = mongoDBObject.containsKey(key)

  def get(key :String) :Object = {

    def lookahead(in :Object, field :String) :Option[AnyRef] = in.isInstanceOf[Map[_, _]] match {
      case true =>
        val innerMap = in.asInstanceOf[Map[String, AnyRef]]
        (innerMap.keySet.toList == List(field)) match {
          case true => innerMap.get(field)
          case false => None
        }
      case false => None
    }

    val o = mongoDBObject.as[Object](key)
    o match {
      case s :Some[_] => s.asInstanceOf[Some[AnyRef]].get.asInstanceOf[Object]
      case oid :ObjectId => oid.asInstanceOf[ObjectId]
      /* warning, can throw NoneException */
      case m if lookahead(m, "$oid").isDefined => lookahead(m, "$oid").map( anyRef => new ObjectId(anyRef.asInstanceOf[String])).get.asInstanceOf[Object]
      case m if lookahead(m, "$date").isDefined => lookahead(m, "$date").map { innerValue => innerValue match {
        case x if x.isInstanceOf[String] => DateTime.parse(innerValue.asInstanceOf[String]).asInstanceOf[Object]
        case j if lookahead(j, "$numberLong").isDefined => lookahead(j, "$numberLong").map { innerValue => new DateTime(innerValue.asInstanceOf[String].toLong) }.get
      }}.get
      case m if lookahead(m, "$numberLong").isDefined => lookahead(m, "$numberLong").map { longStr => longStr.asInstanceOf[String].toLong }.get.asInstanceOf[Object]
      case v :Vector[_] => MongoDBList(v.asInstanceOf[Vector[AnyRef]]).asInstanceOf[Object]
      case x => x.asInstanceOf[Object]
    }
  }

  def keySet() :java.util.Set[String] = mongoDBObject.keySet.asJava
  def put(key :String, value :Object) = mongoDBObject += (key -> value)
  def removeField(key :String) = mongoDBObject.removeField(key)

  override def toString() :String = {
    s"DB OBJECT: ${mongoDBObject}"
  }
}

