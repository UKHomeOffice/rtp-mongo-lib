package uk.gov.homeoffice.mongo

import cats.data.EitherT
import cats.effect.IO
import cats.implicits._
import cats.effect.std.Queue
import org.mongodb.scala.{Observable, Observer}
import scala.concurrent.{Future, ExecutionContext}

import uk.gov.homeoffice.mongo.model.syntax._

object MongoHelpers {
  import uk.gov.homeoffice.mongo.model._

  def fromObservable[A](observable :Observable[A]) :fs2.Stream[IO, MongoResult[A]] = {
    import cats.effect.std.{Dispatcher, Queue}
    import cats.effect.implicits._

    for {
      d <- fs2.Stream.resource(Dispatcher.sequential[IO])
      q <- fs2.Stream.eval(Queue.unbounded[IO, Option[MongoResult[A]]])
      _ <- fs2.Stream.suspend {
        def enqueue(v: Option[MongoResult[A]]): Unit = d.unsafeRunAndForget(q.offer(v))
        observable.subscribe(new Observer[A] {
          override def onNext(t: A): Unit = enqueue(Some(Right(t)))
          override def onError(throwable: Throwable): Unit = enqueue(Some(Left(MongoError(s"Streaming error: ${throwable}"))))
          override def onComplete(): Unit = enqueue(None)
        })
        fs2.Stream.emit(())
      }
      qnt <- fs2.Stream.fromQueueNoneTerminated(q)
    } yield { qnt }
  }

  def futureToIO[A](future :scala.concurrent.Future[A]) :IO[A] =
    IO.fromFuture(IO(future))

  def futureToIOMongoResult[A](future :scala.concurrent.Future[A])(implicit ec :ExecutionContext) :IO[MongoResult[A]] =
    futureToIO[A](future)
      .attempt
      .map { _.left.map { throwable => MongoError(s"MONGO EXCEPTION: ${throwable.getMessage}") }}

  def mongoResultCollect[B](in :List[MongoResult[B]]) :MongoResult[List[B]] = {
    in.collect { case Left(mongoResult) => mongoResult } match {
      case Nil => Right(EitherT(in).collectRight)
      case listOfErrors => Left(MongoError(listOfErrors.map(_.message).mkString(",")))
    }
  }

}
