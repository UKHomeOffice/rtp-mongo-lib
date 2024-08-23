package uk.gov.homeoffice.mongo.casbah

import uk.gov.homeoffice.mongo.casbah._

/*
 * A lot of existing code uses inheritance. e.g. MyRepository extends Repository
 *
 * This allows you to have one object that can call MyRepository and Repository
 * at the same time. e.g.
 *
 *   lockRepository.releaseLock      // specific to MyRepository
 *   lockRepository.drop             // a generic function from Repository
 *
 * The problem with this approach is that it means to make a MyRepository, a
 * caller must know how to make a Repository at the same time. Either this
 * complicates the call stack (e.g. new ProcessRepository(primaryKeys, collectionName)..)
 *
 * Or the system uses traits with undefined members. That's what the old system did as
 * is commonly referred to as the "cake pattern". This is often an anti-pattern.
 *
 * To move away from cake, to minimise code changes in callers and to simplify object
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
