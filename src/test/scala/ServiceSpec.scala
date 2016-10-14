
import akka.actor.{ActorSystem, Props}
import akka.event.NoLogging
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.{HttpEntity, HttpMethod, HttpRequest, HttpResponse}
import akka.http.scaladsl.server.MethodRejection
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.stream.scaladsl.Tcp.{ServerBinding, IncomingConnection}
import akka.stream.scaladsl.{Tcp, Source, Flow}
import akka.util.Timeout
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

abstract class ServiceSpec extends Matchers  with Eventually with WordSpecLike with ScalatestRouteTest with Service with MockitoSugar with BeforeAndAfterAll  {
  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(20).second)

  implicit val actorRefFactory: ActorSystem = system

  override implicit val timeout = Timeout(60 seconds)
  implicit val cache = system.actorOf(Props(new DBActorSupervisor()))


  override def testConfigSource = "akka.loglevel = WARNING"
  override def config = testConfig
  override val logger = NoLogging

"Service" should {"respond with OK when POST for a student" in {
    Post("/entity").withEntity(
      """{"UUID":"1387987"}
      """.stripMargin) ~> endpoints ~> check {
      eventually {
        status shouldBe OK
        responseAs[String].length should be > 0
      }
    }
  }}

  override val connections: Source[IncomingConnection, Future[ServerBinding]] = Tcp().bind(ConfigurationService.getSourceHostname, ConfigurationService.getSourcePort)
}

object ServiceSpec  {
  // Define your test specific configuration here
  val config = """
    akka {
      loglevel = "WARNING"
    }
               """
}
