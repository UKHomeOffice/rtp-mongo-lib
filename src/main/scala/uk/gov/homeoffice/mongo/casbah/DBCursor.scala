package uk.gov.homeoffice.mongo.casbah

// TODO: This isn't implemented correctly yet!
//
// _Only here to aid compilation_. We may need
// delayed execution of the query for sort/limit
// etc to work and be done server side.

case class DBCursor[A](items :List[A]) {
  def apply() :List[A] = items
  def toList() :List[A] = items
  def limit(n :Int) :DBCursor[A] = this
  def sort(orderBy :MongoDBObject) :DBCursor[A] = this
  def skip(n :Int) :DBCursor[A] = this

  def map[B](fn :A => B) :DBCursor[B] = DBCursor[B](items.map(fn))
}

