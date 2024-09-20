package uk.gov.homeoffice.mongo.casbah

import uk.gov.homeoffice.mongo.casbah._

/*
 * A lot of existing code uses inheritance and the cake pattern. e.g. MyRepository extends Repository
 *
 * This allows them to execute any repository functions they want from outside the class, e.g.
 *
 *   lockRepository.releaseLock      // specific to MyRepository
 *   lockRepository.drop             // a generic function from Repository
 *
 * To move away from the cake pattern and to minimise code changes in callers and to simplify object
 * creation we suggest people implementing a repository use this, which allows for easier
 * refactoring later.
 *
 * This is effectively 1 layer of inheritance for compatibility, but not the huge chains of inheritance
 * we used to have.
*/

trait CasbahPassthrough[A] {

  val collection :MongoCasbahSalatRepository[A]

  def insert(a :A) :A = collection.insert(a)
  def save(a :A) :A = collection.save(a)
  def findOne(q :MongoDBObject) :Option[A] = collection.findOne(q)
  def find(q :MongoDBObject) :DBCursor[A] = collection.find(q)
  def find(q :MongoDBObject, p :MongoDBObject) :DBCursor[A] = collection.find(q, p)
  def aggregate(q :List[MongoDBObject]) :List[MongoDBObject] = collection.aggregate(q)
  def drop() :Unit = collection.drop()
  def remove(q :MongoDBObject) :CasbahDeleteResult = collection.remove(q)

}
