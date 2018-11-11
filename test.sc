import fs2._
import fs2.events._
import cats.effect._
import scala.util.Random

case object Next

sealed trait Event
case class Initialized(i: Int) extends Event
case object Added1 extends Event
case object Halfed extends Event
case object Tripled extends Event

class MyEventSourcing extends EventSourcing[IO, Next.type, Event, Int](new store.InMemoryEventStore) {
  def validateCommand(state: Option[Int], command: Next.type): IO[Seq[Event]] =
    state match {
      case Some(oldValue) =>
        IO {
          if(oldValue % 2 == 0)
            Seq(Halfed)
          else
            Seq(Tripled, Added1)
        }
      case None =>
        IO(Seq(Initialized(Random.nextInt(20) + 1)))
    }

  def interpretEvent(state: Option[Int], event: Event): Option[Int] = {
    (state, event) match {
      case (None, Initialized(i)) => Some(i)
      case (Some(i), Added1) => Some(i + 1)
      case (Some(i), Halfed) => Some(i / 2)
      case (Some(i), Tripled) => Some(i * 3)
    }
  }

}

def go() = {
  val stream = fs2.Stream("k1" -> Next, "k2" -> Next, "k1" -> Next).repeat.covary[IO].through(new MyEventSourcing().pipe).take(10)
  println(stream.compile.toVector.unsafeRunSync())
}
