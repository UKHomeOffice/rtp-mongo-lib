package uk.gov.homeoffice.mongo.casbah

class MongoDBObjectBuilder(init :MongoDBObject = MongoDBObject.empty()) {

  var obj :MongoDBObject = init

  def +=[A](items :(String, A)*) :Unit = items.foreach { case (k, v) => obj += (k -> v) }
  def +=(other :MongoDBObject) :Unit = other.data.foreach { case (k, v) => obj += (k -> v) }
  def ++(other :MongoDBObject) :MongoDBObjectBuilder = this.++(other)
  def merge(other :MongoDBObject) :MongoDBObjectBuilder = this.++(other)
  def put(items :(String, Object)*) :Unit = this.+=(items.toList :_*)
  def putAll(other :MongoDBObject) :Unit = this.+=(other.data.toList :_*)
  def result() :MongoDBObject = obj

  override def toString() :String = { s"MONGO DB OBJECT BUILDER: $obj" }

}
