package uk.gov.homeoffice.mongo.casbah

object syntax {

  type DBObject = MongoDBObject
  type BasicDBList = MongoDBList[MongoDBObject]

  implicit class SyntaxOps(val underlying :String) extends AnyVal {
    def `$set`[A](in :(String, A)*) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$set" -> in))

    def `$eq`[A](in :(String, A)*) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$eq" -> in))
    def `$eq`[A](in :A) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$eq" -> in))

    def `$ne`[A](in :(String, A)*) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$ne" -> in))
    def `$ne`[A](in :A) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$ne" -> in))

    def `$gt`[A](in :(String, A)*) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$gt" -> in))
    def `$gt`[A](in :A) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$gt" -> in))

    def `$gte`[A](in :(String, A)*) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$gte" -> in))
    def `$gte`[A](in :A) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$gte" -> in))

    def `$lt`[A](in :(String, A)*) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$lt" -> in))
    def `$lt`[A](in :A) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$lt" -> in))

    def `$lte`[A](in :(String, A)*) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$lte" -> in))
    def `$lte`[A](in :A) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$lte" -> in))

    def `$exists`[A](in :(String, A)*) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$exists" -> in))
    def `$exists`[A](in :A) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$exists" -> in))

    def `$in`[A](in :List[A]) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$in" -> in))
    def `$in`[A](in :Iterable[A]) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$in" -> in))
  }

  def `$set`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$set" -> in)
  def `$eq`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$eq" -> in)

  def `$and`[A](a :MongoDBObject, b :MongoDBObject) :MongoDBObject = MongoDBObject("$and" -> List(a, b))
  def `$or`[A](a :MongoDBObject, b :MongoDBObject) :MongoDBObject = MongoDBObject("$or" -> List(a, b))

  def `$gt`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$gt" -> in)
  def `$gte`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$gte" -> in)
  def `$lt`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$lt" -> in)
  def `$lte`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$lte" -> in)
  def `$exists`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$exists" -> in)
  def `$in`[A](in :List[A]) :MongoDBObject = MongoDBObject("$in" -> in)
  def `$in`[A](in :Iterable[A]) :MongoDBObject = MongoDBObject("$in" -> in)

  // TODO: untested and probably not working as A => Obj / Json not defined. Only here from compilation atm
  def dateRangeQuery[A](from :Option[A], to :Option[A]) = (from, to) match {
    case (None, None) => MongoDBObject()
    case (Some(f), None) => MongoDBObject("$gte" -> f)
    case (None, Some(t)) => MongoDBObject("$lte" -> t)
    case (Some(f), Some(t)) => MongoDBObject("$gte" -> f, "$lte" -> t)
  }

}
