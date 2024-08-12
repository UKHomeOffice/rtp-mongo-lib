package uk.gov.homeoffice.mongo.casbah

// TODO: This isn't implemented correctly yet.
// Only here to aid compilation. We may need
// delayed execution of the query for sort/limit
// etc to work.

case class DBCursor(items :List[MongoDBObject]) {
  def apply() :List[MongoDBObject] = items
  def toList() :List[MongoDBObject] = items
  def limit(n :Int) :DBCursor = this
  def sort(orderBy :MongoDBObject) :DBCursor = this
  def skip(n :Int) :DBCursor = this

}

