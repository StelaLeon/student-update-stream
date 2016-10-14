import akka.actor.{Props, ActorSystem}
import akka.stream.{ActorMaterializerSettings, ActorMaterializer}
import akka.util.Timeout
import org.scalatest.mock.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}
import recovery.RestartStreamStrategy
import spray.json._
import scala.concurrent.duration._

import scala.io.Source

import org.mockito.Mockito._

/**
 * Created by sleon on 8/19/16.
 */
abstract class StudentsParserUnitTest extends FlatSpec with Matchers  {

  //implicit val cache:DBOps = mock[DBOps]
   implicit val system = ActorSystem()
   implicit val executor = system.dispatcher

   implicit val timeout = Timeout(3 minutes)

  implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(system)
      .withSupervisionStrategy(new RestartStreamStrategy)
  )

   implicit val cache = system.actorOf(Props(new DBActorSupervisor()))

  "Market format" should "parse correctly one market from sample file" in {
    //when(cache.getValueFromCache("")).thenReturn("")

    val source: String = Source.fromURL(getClass.getResource("/single_student.json")).getLines.mkString
    val parser = EntityParser()
    val result = parser.contentFormat.read(source.parseJson)

    //val resultAllMarkets = parser.parseEntityAndUpdateCache()

    result.studentID shouldBe 1387987
  }



}
