package fs2
package events
package store

import scala.language.higherKinds

/** The core of the event sourcing storage framework.
  */
trait EventStore[F[_], Event] {

  /** Persists the `events` to the store for the given `id`.
    * The sequence must be persisted atomically.
    */
  def writeEvents(id: String, events: Seq[(Long, Event)]): F[Unit]

  /** Loads all events from the event store for the given `id`.
    * If `from` is specified, only events from the given sequence number (inclusive)
    * will be returned.
    *
    * This is used when recovering a state for a given persisted entity.
    */
  def loadEvents(id: String, from: Option[Long]): Stream[F, StoredEvent[Event]]

}

case class StoredEvent[Event](sequenceNr: Long, event: Event)
