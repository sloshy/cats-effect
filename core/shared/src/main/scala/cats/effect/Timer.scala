/*
 * Copyright (c) 2017-2018 The Typelevel Cats-effect Project Developers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cats.effect

import scala.annotation.implicitNotFound
import scala.concurrent.duration.{FiniteDuration, TimeUnit}

/**
 * Timer is a scheduler of tasks.
 *
 * This is the purely functional equivalent of:
 *
 *  - Java's
 *    [[https://docs.oracle.com/javase/9/docs/api/java/util/concurrent/ScheduledExecutorService.html ScheduledExecutorService]]
 *  - JavaScript's
 *    [[https://developer.mozilla.org/en-US/docs/Web/API/WindowOrWorkerGlobalScope/setTimeout setTimeout]].
 *
 * It provides:
 *
 *  1. the ability to get the current time
 *  1. thread / call-stack shifting
 *  1. ability to delay the execution of a task with a specified time duration
 *
 * It does all of that in an `F` monadic context that can suspend
 * side effects and is capable of asynchronous execution (e.g. [[IO]]).
 *
 * This is NOT a type-class, as it does not have the coherence
 * requirement.
 */
@implicitNotFound("""Cannot find implicit value for Timer[${F}].
Note that ${F} needs to be a cats.effect.Async data type. You might also
need a scala.concurrent.ExecutionContext in scope, or equivalent, try to
import scala.concurrent.ExecutionContext.Implicits.global
""")
trait Timer[F[_]] {
  /**
   * Returns the current time, as a Unix timestamp (number of time units
   * passed since the epoch), suspended in `F[_]`.
   *
   * This is the pure equivalent to Java's
   * [[https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#currentTimeMillis-- System.currentTimeMillis]].
   *
   * The provided `TimeUnit` determines the time unit of the output.
   * For example this will return the number of milliseconds since
   * the epoch:
   *
   * {{{
   *   import scala.concurrent.duration.MILLISECONDS
   *
   *   timer.currentTime(MILLISECONDS)
   * }}}
   *
   * N.B. the granularity is limited by the underlying implementation
   * and by the underlying CPU and OS. For example:
   *
   *  - if the implementation uses `System.currentTimeMillis`,
   *    then it can't have a better granularity than 1 millisecond,
   *    plus depending on underlying runtime (e.g. Node.js) it might
   *    return multiples of 10 milliseconds
   *  - if the implementation uses `System.nanoTime`, then that's
   *    the best granularity you can have, but note that its behavior
   *    is highly dependent on the underlying runtime, OS and CPU and
   *    on top of JavaScript / Node.js (at the moment of writing) the
   *    granularity of `nanoTime` is in milliseconds
   *
   * The default `Timer` implementation uses `System.currentTimeMillis`.
   * But if you want `System.nanoTime`, you can always implement your own.
   */
  def currentTime(unit: TimeUnit): F[Long]

  /**
   * Creates a new task that will sleep for the given duration,
   * emitting a tick when that time span is over.
   *
   * As an example on evaluation this will print "Hello!" after
   * 3 seconds:
   *
   * {{{
   *   import cats.effect._
   *   import scala.concurrent.duration._
   *
   *   Timer[IO].sleep(3.seconds).flatMap { _ =>
   *     IO(println("Hello!"))
   *   }
   * }}}
   *
   * Note that `sleep` is required to introduce an asynchronous
   * boundary, even if the provided `timespan` is less or
   * equal to zero.
   */
  def sleep(duration: FiniteDuration): F[Unit]

  /**
   * Asynchronous boundary described as an effectful `F[_]` that
   * can be used in `flatMap` chains to "shift" the continuation
   * of the run-loop to another thread or call stack.
   *
   * This is the [[Async.shift]] operation, without the need for an
   * `ExecutionContext` taken as a parameter.
   *
   * This `shift` operation can usually be derived from `sleep`:
   *
   * {{{
   *   timer.shift <-> timer.sleep(Duration.Zero)
   * }}}
   */
  def shift: F[Unit]
}

object Timer {
  /**
   * For a given `F` data type fetches the implicit [[Timer]]
   * instance available implicitly in the local scope.
   */
  def apply[F[_]](implicit timer: Timer[F]): Timer[F] = timer

  /**
   * Derives a [[Timer]] for any type that has a [[LiftIO]] instance,
   * from the implicitly available `Timer[IO]` that should be in scope.
   */
  def derive[F[_]](implicit F: LiftIO[F], timer: Timer[IO]): Timer[F] =
    new Timer[F] {
      def shift: F[Unit] =
        F.liftIO(timer.shift)
      def sleep(timespan: FiniteDuration): F[Unit] =
        F.liftIO(timer.sleep(timespan))
      def currentTime(unit: TimeUnit): F[Long] =
        F.liftIO(timer.currentTime(unit))
    }
}
