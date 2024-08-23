package uk.gov.homeoffice.mongo.casbah

import scala.reflect.ClassTag
import org.joda.time.DateTime

object syntax {

  type BasicDBList = MongoDBList[MongoDBObject]

  implicit def mongoDBObjectDowngrade(a :MongoDBObject) :DBObject = { new DBObject(a) }

  implicit class SyntaxOps(val underlying :String) extends AnyVal {
    def `$set`[A](in :(String, A)*) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$set" -> MongoDBObject(in :_*)))
    def `$unset`[A](in :(String, A)*) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$unset" -> MongoDBObject(in :_*)))

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

    def `$nin`[A : ClassTag](in :List[A]) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$nin" -> in.toArray))
    def `$nin`[A : ClassTag](in :Iterable[A]) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$nin" -> in.toArray))

    def `$push`[A](in :(String, A)*) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$push" -> in))
    def `$push`[A](in :A) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$push" -> in))

    def `$each`[A](in :(String, A)*) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$each" -> in))
    def `$each`[A](in :A) :MongoDBObject = MongoDBObject(underlying -> MongoDBObject("$each" -> in))
  }

  def `$set`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$set" -> MongoDBObject(in :_*))
  def `$unset`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$unset" -> MongoDBObject(in :_*))

  def `$eq`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$eq" -> MongoDBObject(in :_*))

  def `$and`(in :MongoDBObject*) :MongoDBObject = MongoDBObject("$and" -> in.toArray[MongoDBObject])
  def `$or`(in :MongoDBObject*) :MongoDBObject = MongoDBObject("$or" -> in.toArray[MongoDBObject])

  def `$gt`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$gt" -> in)
  def `$gte`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$gte" -> in)

  def `$lt`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$lt" -> in)
  def `$lte`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$lte" -> in)

  def `$exists`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$exists" -> in)

  def `$in`[A : ClassTag](in :List[A]) :MongoDBObject = MongoDBObject("$in" -> in.toArray)
  def `$in`[A : ClassTag](in :Iterable[A]) :MongoDBObject = MongoDBObject("$in" -> in.toArray)

  def `$nin`[A : ClassTag](in :List[A]) :MongoDBObject = MongoDBObject("$nin" -> in.toArray)
  def `$nin`[A : ClassTag](in :Iterable[A]) :MongoDBObject = MongoDBObject("$nin" -> in.toArray)

  def `$push`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$push" -> in)

  def `$each`[A](in :(String, A)*) :MongoDBObject = MongoDBObject("$each" -> in)

  def dateRangeQuery(from :Option[DateTime], to :Option[DateTime]) :Option[MongoDBObject] = (from, to) match {
    case (None, None) => None
    case (Some(f), None) => Some(MongoDBObject("$gte" -> f))
    case (None, Some(t)) => Some(MongoDBObject("$lte" -> t))
    case (Some(f), Some(t)) => Some(MongoDBObject("$gte" -> f, "$lte" -> t))
  }

}
