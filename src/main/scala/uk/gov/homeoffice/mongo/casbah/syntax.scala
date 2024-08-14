package uk.gov.homeoffice.mongo.casbah

import scala.reflect.ClassTag

object syntax {

  type BasicDBList = MongoDBList[MongoDBObject]

  implicit def mongoDBObjectDowngrade(a :MongoDBObject) :DBObject = { new DBObject(a) }

  implicit class SyntaxOps(val underlying :String) extends AnyVal {
    def `$set`[A](in :(String, A)*) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$set" -> MongoDBObject(in :_*)))

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

    def `$in`[A : ClassTag](in :List[A]) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$in" -> in.toArray))
    def `$in`[A : ClassTag](in :Iterable[A]) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$in" -> in.toArray))
  }

  def `$set`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$set" -> MongoDBObject(in :_*))
  def `$eq`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$eq" -> MongoDBObject(in :_*))

  def `$and`(a :MongoDBObject, b :MongoDBObject) :MongoDBObject = MongoDBObject("$and" -> Array[MongoDBObject](a, b))
  def `$or`(a :MongoDBObject, b :MongoDBObject) :MongoDBObject = MongoDBObject("$or" -> Array[MongoDBObject](a, b))

  def `$gt`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$gt" -> in)
  def `$gte`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$gte" -> in)
  def `$lt`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$lt" -> in)
  def `$lte`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$lte" -> in)
  def `$exists`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$exists" -> in)
  def `$in`[A : ClassTag](in :List[A]) :MongoDBObject = MongoDBObject("$in" -> in.toArray)
  def `$in`[A : ClassTag](in :Iterable[A]) :MongoDBObject = MongoDBObject("$in" -> in.toArray)

  // TODO: untested and probably not working as A => Obj / Json not defined. Only here from compilation atm
  def dateRangeQuery[A](from :Option[A], to :Option[A]) = (from, to) match {
    case (None, None) => MongoDBObject()
    case (Some(f), None) => MongoDBObject("$gte" -> f)
    case (None, Some(t)) => MongoDBObject("$lte" -> t)
    case (Some(f), Some(t)) => MongoDBObject("$gte" -> f, "$lte" -> t)
  }

}
