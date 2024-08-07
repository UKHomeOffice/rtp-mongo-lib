
RTP Mongo Library - Scala library to work with Mongodb drivers
==============================================================

Created in 2015, for Scala 2.11, this library bought [Mongo Casbah](https://mongodb.github.io/casbah/), the official Mongo driver at the time, and [Salat](https://github.com/salat/salat) together to create a seamless ORM for serialising domain objects to the database and writing effective Mongo queries.

With Mongo Casbah abandoned and Salat un-portable due to its reliance on scalac internal apis our large business apps are held back on Scala 2.12 where support is slowly withering. This branch aims to rework the core platform:

* moving us to the [latest official Mongo drivers](https://www.mongodb.com/docs/languages/scala/scala-driver/current/)
* Using [Circe JSON](https://circe.github.io/circe/) (intentionally instead of codecs) for a platform independent serialisation solution
* APIs that tightly mirror the major Casbah APIs we use such as MongoDBObject, MongoDBList and functions such as .save(), .find() .aggregate().

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

Get streaming features, backed by [fs2](https://fs2.io/#/getstarted/install) for a low-memory, high performance solution

```scala
  // turn a collection into a repository and gain access to fs2 features.
  val basicBookRepository = new MongoStreamRepository(globalDatabaseConnection, "books", List("_id"))

  val allBooks = basicBookRepository.all().compile.toList.unsafeRunSync()
  println(s"All Books: ${allBooks}")
```

Provide A => Json and vice-versa to get serialisation of objects to and from the DB.

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

  // notice that since ApiEncoder is imported, numbers converted to json like this: { "isbn" : 123 }

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

val casbahRepo = new MongoCasbahSalatRepository(autoBookRepository)

val aliceInWonderland = casbahRepo.save(Book("Alice in Wonderland", "Carol", "678234832"))

val results :List[Records] = casbah.find(MongoDBObject("createdDate" -> ("$gt" -> DateTime.now)))

```
