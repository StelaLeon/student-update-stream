package recovery

import java.util.concurrent.TimeoutException
import akka.actor.ActorSystem
import akka.stream.Supervision
import akka.stream.Supervision.Directive
import com.typesafe.scalalogging.LazyLogging


/**
 * Created by sleon on 8/25/16.
 */
class RestartStreamStrategy (implicit actorSystem: ActorSystem) extends Supervision.Decider with LazyLogging {
  override def apply(ex: Throwable): Directive = {
    logger.error(s"We encounter some problems: ${ex}. Appying the restart stream strategy.")
    ex match {
      case _: IllegalArgumentException => {
        Supervision.Stop
      }
      case _: NullPointerException | _:TimeoutException => {
        logger.error(s"We encounter the following exception ${ex} , restarting the stream.")
        Supervision.Restart
      }
      case default => Supervision.Resume
    }
  }
}
