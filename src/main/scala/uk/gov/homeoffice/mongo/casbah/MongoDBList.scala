package uk.gov.homeoffice.mongo.casbah

class MongoDBList[A](val array :Array[A])(implicit tag :scala.reflect.ClassManifest[A]) {
  def underlying :List[A] = array.toList
  def toList :List[A] = array.toList
  def toArray :Array[A] = array
}

object MongoDBList {
  def apply[A](varadic :A*)(implicit tag :scala.reflect.ClassManifest[A]) :MongoDBList[A] = new MongoDBList[A](varadic.toArray)
  def apply[A](seq :List[A])(implicit tag :scala.reflect.ClassManifest[A]) :MongoDBList[A] = new MongoDBList[A](seq.toArray)
}
