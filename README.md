
RTP Mongo Library - Scala library to work with Mongodb drivers
==============================================================

Created in 2015, for Scala 2.11, this library bought [Mongo Casbah](https://mongodb.github.io/casbah/), the official Mongo driver at the time, and [Salat](https://github.com/salat/salat) together to create a seamless ORM for serialising domain objects and writing effective Mongo queries.

With Mongo Casbah abandoned and Salat un-portable due to its reliance on scalac internal apis our large business apps are held back on Scala 2.12 where support is slowly withering. This branch aims to rework the core platform:

* Moving us to the [latest official Mongo drivers](https://www.mongodb.com/docs/languages/scala/scala-driver/current/)
* Using [Circe JSON](https://circe.github.io/circe/) (intentionally instead of codecs) for a platform independent serialisation solution
* APIs that tightly mirror the major Casbah APIs we use such as MongoDBObject, MongoDBList and functions such as .save(), .find() .aggregate().

Moving the app to the new underlying driver provides significant benefits over just using a support library. It allows us to move to Scala 2.13, allows upstream apps to move to Scala 2.13, apply security fixes for libraries left behind. It gives us access to Mongo features beyond Mongo 3.2 (now that Mongo 6 is released).

# Sample code:

Use the official objects.

```scala
  // connect to localhost test db, with fully access to the official drivers and all its options
  val globalDatabaseConnection :MongoConnection = MongoConnector.connect(
    "mongodb://localhost/example",
    "ExampleApp",
    false,
    "example"
  )

  Await.result(globalDatabaseConnection.mongoCollection("exampleLogins").insertOne(Document(
    "hello" -> true
  )).head(), Duration.Inf)
```

Get streaming features, backed by [fs2](https://fs2.io/#/getstarted/install) for a low-memory, high performance:

```scala
  // turn a collection into a repository and gain access to fs2 features.
  val basicBookRepository = new MongoStreamRepository(globalDatabaseConnection, "books", List("_id"))

  val allBooks = basicBookRepository.all().compile.toList.unsafeRunSync()
  println(s"All Books: ${allBooks}")
```

Provide `A => io.circe.Json` and vice-versa to get serialisation of objects to and from the DB.

```scala

  def bookToJson(book :Book) :Either[MongoError, io.circe.Json] = { Right(book.asJson) }
  def jsonToBook(json :io.circe.Json) :Either[MongoError, Book] = { decode[Book](json.spaces4) match {
    case Left(circeExc) => Left(MongoError(s"Unable to turn json into a book: ${circeExc.getMessage} (from JSON: ${json.spaces4})"))
    case Right(book) => Right(book)
  }}

  val autoBookRepository = new MongoObjectRepository[Book](new MongoJsonRepository(basicBookRepository)) {
    def jsonToObject(json :Json) :MongoResult[Book] = jsonToBook(json)
    def objectToJson(obj :Book) :MongoResult[Json] = bookToJson(obj)
  }

  // insert a book
  val saveResult = autoBookRepository.insertOne(Book("The Davinci Code", "Mike row", "743927492")).unsafeRunSync()

  println(s"Saving Davinci code returned: $saveResult")

```


Reasons we choose to use a separate JSON serialisation instead of the Mongo driver's codec solution:

* Easier to provide backwards compatibility with Salat. It used to write `_typeHint` fields into the db we want to respect during our migrations without editing domain code.
* We don't want to further couple ourselves to Mongo libraries given the issues we've had with Casbah.
* Having a single way to serialise a domain object to JSON for http front-ends and database end makes our applications much easier to read and reason about, and far more transparent.
* We have successfully ported some Mongo applications to Postgres and we are considering this for other solutions going forwards. We may extend rtp-mongo-lib to also be rtp-postgres-lib!
* It is easier to mock, validate and ensure the correctness of db serialisation when codecs are not used.

We talk to the database using [Extended JSON mode](https://www.mongodb.com/docs/manual/reference/mongodb-extended-json/), which allows dates, object ids and other types to be encoded correctly. This is important for performance and effective queries. The subtle differences between JSON to send to clients in your API vs the JSON you send the DB can differ in certain ways yet still allow huge amounts of code reuse. See this tecnique of basing implicits on top of other implicits:

```scala

  object ApiEncoding {
    implicit val numberLongEncoder :Encoder[Long] = Encoder.encodeLong
    implicit val numberLongDecoder :Decoder[Long] = Decoder.decodeLong
  }

  object DatabaseEncoding {
    implicit val numberLongEncoder: Encoder[Long] = new Encoder[Long] {
      final def apply(l: Long): Json = Json.obj(
        "$numberLong" -> Json.fromString(l.toString)
      )
    }
  }

  // defines a single book encoder

  class MyBookEncoder(
    implicit val numberLongEncoder :Encoder[Long],
    implicit val numberLongDecoder :Decoder[Long],
  ) {

    import io.circe.generic.semiauto._
    implicit val bookEncoder :Encoder[Book] = deriveEncoder[Book]
  }

  // sample code for http interface
  import ApiEncoder._
  implicit bookEnc = new MyBookEncoder()

  HttpOK( myBook.asJson )

  // notice that since ApiEncoder is imported, numbers converted to json like this:

  { "isbn" : 123 }

  // alternatively in our db repositories we can simply import database encoder instead

  import DatabaseEncoding._
  implicit bookEnc = new MyBookEncoder()

  database.save( myBook.asJson )

  // This reuses MyBookEncoder... but since DatabaseEncoding was imported, numbers get
  // serialised the correct way for Mongo which is this:

  // { "isbn" : { "$numberLong" : "123" }}

```

Finally to ease the migration of all our code we provide several APIS that resemble those originally offered with Casbah and Salat.

```scala

val casbahRepo = new MongoCasbahSalatRepository(...)

val results :List[Records] = casbahRepo
    .find(MongoDBObject("createdDate" -> ("$gt" -> DateTime.now)))
    .sort(("_id" -> 1))
    .limit(5)
    .toList

casbahRepo.save(myBook).getN()

```

[Mongo driver documentation is here](https://www.mongodb.com/docs/languages/scala/scala-driver/current/). The [modern API is here](https://mongodb.github.io/mongo-java-driver/5.1/apidocs/mongo-scala-driver/org/mongodb/scala/MongoCollection.html). The [BSON API is here](https://mongodb.github.io/mongo-java-driver/4.11/apidocs/bson/org/bson/types/package-summary.html).

## Why use this library?

You should use the official Mongo drivers and skip depending on this library if possible. This is only helpful in migrating large apps which depend on Casbah to something more modern.

## Implementation concerns.

Whilst this library provides MongoDBObject seemingly compatible with Casbah there are some areas of concern.

### Implementation of Options

Behaviour of `MongoDBObject("someValue" -> option)` should be treated with care. JsonRepository calls `deepDropNullValues` when turning json into mongo documents before them. This provides some level of compatibility with Casbah's behaviour but has some caveats.

The old mongo driver supported calling `update` without providing `$set` at the top level.

In `mongosh` the following command would cause an error:

```bash
> db.test.updateOne({}, { "newValue": "hello" })
Error: the update operation document must contain atomic operators
```

Our compatibility layer lifts the value into $set but where the value is null, the caller should rewrite the query to explicitly use $unset. e.g.:

```scala
val qry = x match {
    case Some(v) => MongoDBObject("$set" -> ("name" -> v))
    case None => MongoDBObject("$unset" -> ("name" -> 1))
}
```

Currently we don't support `collection.distinct`. Instead rewrite the query to use `collection.aggregate`. The typical implementation would be: `{ $group: { $id: null, uniqueValues: $addToSet($someField) }}` however `$id: null` gets dropped due to the `deepDropNullValues` call. It leads to this exception: `a group specification must include an _id`. To get around this try hardcoding `_id` to 0 instead:

```bash
> db.names.find()
{ "_id" : ObjectId("66ed414751342d27f0df15bc"), "name" : "johnson" }
{ "_id" : ObjectId("66ed414c51342d27f0df15bd"), "name" : "may" }
{ "_id" : ObjectId("66ed414f51342d27f0df15be"), "name" : "thatcher" }
{ "_id" : ObjectId("66ed415351342d27f0df15bf"), "name" : "thatcher" }
{ "_id" : ObjectId("66ed415d51342d27f0df15c0"), "name" : "sunak" }
{ "_id" : ObjectId("66ed415e51342d27f0df15c1"), "name" : "sunak" }
> db.names.aggregate([ { $group: { _id: 0, uniqueNames: { $addToSet: "$name" }}}])
{ "_id" : 0, "uniqueNames" : [ "may", "thatcher", "sunak", "johnson" ] }
```

Code wise:

```scala
val uniqueNames :List[String] = collection.distinct('name').toList
```

becomes

```scala
val qry = collection.aggregate(List($group" -> MongoDBObject("_id" -> 0, "uniqueNames" -> ($addToSet -> "$name")))).toList
val uniqueNames :List[String] = qry.headOption.map(_.as[MongoDBList[String]]("uniqueNames").toList).getOrElse(List.empty)

```

### Difficulties with the internal representation of a MongoDBObject.

The internal representation of a MongoDBObject is tricky to keep simple. Particularly because of Mongo's support for *dotted notation*. At a first glance dotted notation suggests that both things are equal:

```scala
val dottedNotationDBObject = MongoDBObject()
dottedNotationObject += ("contact.phone" -> "074155232312")
dottedNotationObject += ("contact.name" -> "alfred")

# vs

val nestedObjectsApproach = MongoDBObject("contact" ->
    MongoDBObject("phone" -> "074155232312")
    MongoDBOBject("name" -> "alfred")
)

```

Therefore considering how overwriting values using the alternating notation should work. The first thing I tried was to create a "normalised" internal notation where dotted notation was silently converted into a nested MongoDBObjects. That allowed this to work:

```scala
# should overwrite phone number?
nestedObjectsApproach += ("contact.phone" -> "36492204638")

```

However the dotted notation is critically important when performing an update:

```scala
# in the database

{ "contact": { "name": "alfred", "phone": "98674396492" }}

val dottedNotationUpdate = MongoDBObject("$set" -> MongoDBObject("contact.phone" -> "82390183"))

db.collection.updateOne(..., dottedNotationUpdate)     // works as expected...

val nestedApproach = MongoDBObject("$set" -> MongoDBObject("contact" -> MongoDBObject("phone" -> "38942232")))

db.collection.updateOne(..., nestedApproach)           // removes everything from contact except phone!!

// Using nested approach causes the name to be deleted because only dotted notation deliberately!!

```

Using dotted notation is the only way to replace a single nested element without overwriting the whole document above it. Conversely using dotted notation everywhere does not work for specifying commands. E.g. this failure:

```bash
> db.names.updateOne({}, { "$set.age" : 56 })
nknown modifier: $set.age. Expected a valid update modifier or pipeline-style update specified as an array
```

Therefore the internal data structure for a MongoDBObject (which is a `Map[String, AnyRef]`) can mix dotted and non-dotted notations and `getAs` attempts to do the right thing where possible but cannot be fully relied upon. Use `toString` or `toJson` to inspect and modify it where appropriate.

Examples of things that aren't correctly implemented include:

```scala

val m = MongoDBObject("myLong" -> 98473924789238l)
m.getAs[Long]("myLong.missingInnerValue") // should return None but actually returns Some(...)

val m = MongoDBObject("hello" -> ("true" -> "someVal"), "hello.chalk" -> 66)
m.getAs[MongoDBObject]("hello").keySet() // does not include chalk when it should

```

### Casbah's API does not give room to define ExecutionContexts for Future or IO

1. There is a chance of an application hard locking up due to the excessive use of global ExecutionContexts. Typical Scala applications would have implicit paramters to pass these around but we need to fix some of the existing function signatures.

2. Callers should be wary that holding cursors may hold streams open for too long.

3. We use DBCursor / StreamObservable classes to allow chaining operations. For instance `find(something).sort(something)` doesn't do the sort client side, it's done by the mongo server, as expected. However where the results come back as fs2 Streams, some errors result are reported upstream by replacing the returned stream with a Stream of one item which contains the error.
