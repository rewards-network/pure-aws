package com.rewardsnetwork.pureaws.sqs

import cats._
import cats.syntax.all._
import cats.effect.Temporal
import fs2.Stream

import scala.concurrent.duration.FiniteDuration

/** The underlying abstraction for consuming a message from SQS. The `T` parameter represents the type for changing the
  * visibility timeout in seconds. In the base module, this is an `Int`, but in the refined module, it is a bounded
  * positive integer, for safety.
  */
trait BaseSqsMessage[F[_], T] {
  val body: String
  val receiptHandle: BaseReceiptHandle[F, T]

  /** If the effect evaluates to `true`, delete this message from SQS, otherwise do nothing. */
  def autoDelete(shouldDelete: F[Boolean])(implicit F: Monad[F]): F[Unit] =
    shouldDelete.flatMap {
      case true  => receiptHandle.delete
      case false => Applicative[F].unit
    }

  /** The same as `autoDelete`, but while concurrently changing the message visibility to the specified value every
    * specified number of seconds. Once the message is processed, the message should no longer be renewed.
    */
  def autoDeleteAndRenew(renewEvery: FiniteDuration, visibilityTimeoutSeconds: T)(
      shouldDelete: F[Boolean]
  )(implicit F: Temporal[F]): F[Unit] = {
    autoDeleteAndRenewStream(renewEvery, visibilityTimeoutSeconds)(shouldDelete).compile.drain
  }

  /** Like `autoDeleteAndRenew` except it returns an FS2 `Stream` */
  def autoDeleteAndRenewStream(renewEvery: FiniteDuration, visibilityTimeoutSeconds: T)(
      shouldDelete: F[Boolean]
  )(implicit F: Temporal[F]): Stream[F, Unit] = {
    val renew = fs2.Stream.awakeEvery[F](renewEvery).evalTap { _ =>
      receiptHandle.changeVisibility(visibilityTimeoutSeconds)
    }

    val go = autoDelete(shouldDelete)

    Stream.eval(go).concurrently(renew)
  }

  /** Change message visibility to the given value in seconds.
    *
    * Example:
    * ```
    * //Visibility timeout with this client is an Int
    * val client: SimpleSqsClient[F] = ???
    *
    * def doSomething: F[Unit] = ???
    *
    * def doSomethingElse: F[Unit] = ???
    *
    * client.streamMessages("myQueue").evalMap { msg =>
    *   for {
    *     _ <- doSomething
    *     _ <- msg.renew(120) //Sets the visibility timeout to 2 minutes
    *     _ <- doSomethingElse
    *   } yield ()
    * }
    * ```
    */
  def renew(visibilityTimeoutSeconds: T): F[Unit] = receiptHandle.changeVisibility(visibilityTimeoutSeconds)
}

/** Describes a type that has a field for `MessageAttributes`. */
trait WithAttributes {
  val attributes: MessageAttributes
}

/** A message received from AWS SQS. Visibility timeout is in integer seconds. Possibly unsafe as it allows non-positive
  * values, or values over 12 hours.
  */
final case class SqsMessage[F[_]](
    body: String,
    receiptHandle: ReceiptHandle[F]
) extends BaseSqsMessage[F, Int]

/** A message received from AWS SQS, with message attributes. */
final case class SqsMessageWithAttributes[F[_]](
    body: String,
    receiptHandle: ReceiptHandle[F],
    attributes: MessageAttributes
) extends BaseSqsMessage[F, Int]
    with WithAttributes
