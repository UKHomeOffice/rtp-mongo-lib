package uk.gov.homeoffice.mongo.casbah

class MongoDBObjectBuilder() {

  var obj = MongoDBObject()

  def +=[A](items :(String, A)*) :Unit = items.foreach { case (k, v) => obj += (k -> v) }
  def +=(other :MongoDBObject) :Unit = other.data.foreach { case (k, v) => obj += (k -> v) }
  def put(items :(String, Object)*) :Unit = this.+=(items.toList :_*)
  def putAll(other :MongoDBObject) :Unit = this.+=(other.data.toList :_*)
  def result() :MongoDBObject = obj

}
