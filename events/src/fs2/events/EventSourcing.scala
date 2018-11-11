package fs2
package events

import store._

import cats._
import cats.effect._
import cats.implicits._
import cats.effect.concurrent._

import com.github.benmanes.caffeine.cache._

import java.util.concurrent.TimeUnit

import scala.language.higherKinds

private case class Cached[State](sequenceNr: Long, value: Option[State])

abstract class EventSourcing[F[_], Command, Event, State](store: EventStore[F, Event])(implicit val F: Sync[F]) {

  def validateCommand(state: Option[State], command: Command): F[Seq[Event]]

  def interpretEvent(state: Option[State], event: Event): Option[State]

  private def selector: Pipe[F, (String, Command), (String, Ref[F, Cached[State]], Command)] = {
    def go(s: Stream[F, (String, Command)],
           cache: Cache[String, Ref[F, Cached[State]]]): Pull[F, (String, Ref[F, Cached[State]], Command), Unit] =
      s.pull.uncons1.flatMap {
        case Some(((id, command), tail)) =>
          Option(cache.getIfPresent(id)) match {
            case Some(cached) =>
              Pull.output1((id, cached, command)) >> go(tail, cache)
            case None =>
              // load the state from the store
              Pull
                .eval(
                  store
                    .loadEvents(id, None)
                    .compile
                    .fold((-1l, Option.empty[State])) {
                      case ((_, state), StoredEvent(sequenceNr, event)) =>
                        (sequenceNr, interpretEvent(state, event))
                    }
                    .flatMap {
                      case (sequenceNr, state) =>
                        Ref.of(Cached(sequenceNr, state)).map { state =>
                          cache.put(id, state)
                          state
                        }
                    })
                .flatMap { state =>
                  Pull.output1((id, state, command))
                } >> go(tail, cache)
          }
        case None =>
          Pull.done
      }
    in =>
      go(in,
         Caffeine
           .newBuilder()
           .expireAfterWrite(10, TimeUnit.MINUTES)
           .maximumSize(10000)
           .build()).stream
  }

  def pipe: Pipe[F, (String, Command), (String, Option[State])] =
    _.through(selector).evalMap {
      case (id, state, command) =>
        for {
          cached <- state.get
          Cached(sequenceNr, originalState) = cached
          events <- validateCommand(originalState, command)
          _ <- store.writeEvents(id, ((sequenceNr + 1) to (sequenceNr + events.size)).zip(events))
          newState = events.foldLeft(originalState)(interpretEvent(_, _))
          _ <- if(events.nonEmpty) state.set(Cached(sequenceNr + events.size, newState)) else F.unit
        } yield (id, newState)
    }

}
