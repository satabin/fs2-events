package fs2
package events
package store

import cats._

import scala.collection.{mutable => mu}
import scala.language.higherKinds

class InMemoryEventStore[F[_], Event](implicit val F: Monad[F]) extends EventStore[F, Event] {

  private val entities = mu.Map.empty[String, mu.ListBuffer[StoredEvent[Event]]]

  def loadEvents(id: String, from: Option[Long]): Stream[F, StoredEvent[Event]] =
    entities.get(id).fold[Stream[F, StoredEvent[Event]]](Stream.empty)(evts => Stream.emits(evts.toSeq))

  def writeEvents(id: String, events: Seq[(Long, Event)]): F[Unit] =
    F.pure(entities.getOrElseUpdate(id, new mu.ListBuffer) ++= events.map {
      case (sequenceNr, event) => StoredEvent(sequenceNr, event)
    })
}
