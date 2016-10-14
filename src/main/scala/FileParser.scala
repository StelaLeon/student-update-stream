import akka.actor.{ActorSystem, ActorRef}
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import spray.json.{DefaultJsonProtocol, _}

case class Entity(studentID: Long,
                  town: Option[String])


trait Parsers extends DefaultJsonProtocol {//with NullOptions{
  implicit def contentFormat = jsonFormat2(Entity)
}

case class EntityParser(implicit cache: ActorRef, am:ActorMaterializer, system: ActorSystem) extends Parsers with LazyLogging{

  def parseEntityAndUpdateCache(content: String) = {
    logger.info(s"[EntityParser] Parsing file, searching for resource.")
      val resourceToUpdate = contentFormat.read(content.parseJson)
      cache ! new UpdatePostgres(Some(resourceToUpdate.studentID.toString),"student_info",contentFormat.write(resourceToUpdate).toString())

  }
}


