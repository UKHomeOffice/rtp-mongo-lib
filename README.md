RTP Mongo Library - Scala library to work with Mongodb drivers
==============================================================

Application built with the following (main) technologies:

- Scala

- SBT

- Casbah

- Salat

Introduction
------------
A library to easily "configure" your application to interact with Mongodb, currently via the Casbah driver.

Configuration, as well as using the standard reference/application.conf, is done via mixins to your application and test code.

To interact with Mongodb, choose to either utilise a Casbah or Salat Repository.

A Casbah Repository represents a Mongodb collection to save and get back JSON.

A Salat Repository represents a Mongodb collection to save a "domain" object (a case class) and get back said object.

Build and Deploy
----------------
The project is built with SBT. On a Mac (sorry everyone else) do:
> brew install sbt

It is also a good idea to install Typesafe Activator (which sits on top of SBT) for when you need to create new projects - it also has some SBT extras, so running an application with Activator instead of SBT can be useful. On Mac do:
> brew install typesafe-activator

To compile:
> sbt compile

or
> activator compile

To run the specs:
> sbt test

The following packages up this library - Note that "assembly" will first compile and test:
> sbt assembly

Casbah Repository Example
-------------------------
```scala
import uk.gov.homeoffice.mongo.casbah.Repository

trait ThingsRepository extends Repository {
  val collectionName = "things"
}

val thingsRepository = new ThingsRepository with MyMongo
thingsRepository.collection.save(<my JSON>)

// OR if you "apply" the instance of the repository being created (a la JavaScript), then you don't need to call "collection" before calling an API method such as "save", "find" etc.

val thingsRepository = (new ThingsRepository with MyMongo)()
thingsRepository.save(<my JSON>)
```

Of course, where did that "MyMongo" come from? Each repository, whether Casbah or Salat, needs a "Mongo" mixed in, where the Mongo trait wraps the actual connection to Mongodb and so needs to be configured with the Mongodb details e.g. host, port, credentials.

MyMongo could be coded as:
```scala
import com.typesafe.config.ConfigFactory
import com.mongodb.casbah.MongoClientURI

trait MyMongo extends Mongo {
  lazy val db = MyMongo.mydb
}

object MyMongo {
  lazy val mydb = Mongo db MongoClientURI(ConfigFactory.load.getString("mydb"))
}
```

Hey! That seems like a lot of extra set up code?

Well, the trait (MyMongo) is a must, as we need this to mixin to all your repositories for a particular Mongodb.

Then the object reads the actual configuration - but why here, why not in the trait? For example, why not just do the following?
```scala
trait MyMongo extends Mongo {
  lazy val db = Mongo db MongoClientURI(ConfigFactory.load.getString("mydb"))
}
```

Big mistake! Everytime the trait is now mixed in, not only is a new Mongo connection created, an actual Mongo pool of connections are created - you'll soon have many connection pools and your application will quickly slow down.

Testing
-------
There are two available specifications for tests that interact with Mongo.
MongoSpecification, which requires Mongo to be running locally, and EmbeddedMongoSpecification which has an embedded Mongo per specification.

Via EmbeddedMongoSpecification, each example, within a specification, is run sequentially, as a test database is created and dropped.

And via MongoSpecification, a unique test database is generated per example of a specification, allowing each to run (by default) in parallel.