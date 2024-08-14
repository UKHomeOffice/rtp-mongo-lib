package uk.gov.homeoffice.mongo.casbah

class MongoDBList[A](val array :Array[A])(implicit tag :scala.reflect.ClassManifest[A]) {
  def underlying() :List[A] = array.toList
  def toList() :List[A] = array.toList
  def toArray() :Array[A] = array

  override def toString :String = {
    val items = array.map {
      case s :String => s
      case o => o.toString()
    }.mkString(",")
    s"MONGO DB LIST [$items]"
  }
  override def equals(other :Any) = other.isInstanceOf[MongoDBList[A]] match {
    case false => false
    case true => other.asInstanceOf[MongoDBList[A]].toList.equals(array.toList)
  }
}

object MongoDBList {
  def apply[A](varadic :A*)(implicit tag :scala.reflect.ClassManifest[A]) :MongoDBList[A] = new MongoDBList[A](varadic.toArray)
  def apply[A](seq :List[A])(implicit tag :scala.reflect.ClassManifest[A]) :MongoDBList[A] = new MongoDBList[A](seq.toArray)
  def apply[A](vector :Vector[A])(implicit tag :scala.reflect.ClassManifest[A]) :MongoDBList[A] = new MongoDBList[A](vector.toArray)
}
