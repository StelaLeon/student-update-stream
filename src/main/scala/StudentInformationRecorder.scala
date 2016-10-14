import java.nio.ByteOrder

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.{Logging, LoggingAdapter}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream._
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl._
import akka.util.{ByteString, Timeout}
import com.typesafe.config.{Config, ConfigFactory}
import kamon.Kamon
import org.apache.commons.lang.StringEscapeUtils
import persistency.{PersistenceModuleImpl, SchemaGeneration}
import recovery.RestartStreamStrategy
import spray.json.{JsValue, _}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

trait Service {
  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: ActorMaterializer
  implicit val cache: ActorRef
  implicit val timeout: Timeout
  def DELIMITER="""DELIM"""
  implicit val byteOrder = ByteOrder.LITTLE_ENDIAN
  def config: Config
  val logger: LoggingAdapter

  val connections: Source[IncomingConnection, Future[ServerBinding]]

  val endpoints = {
    logRequestResult("simple-http-akka-streams") {
        path(ConfigurationService.getEntityEndpoint) {
          (post){
          decodeRequest{
            entity(as[String]){ jsonStr=>
              complete{
                val entityID = getEntityID(jsonStr.parseJson.asJsObject().getFields("uuid"))

                logger.info(s"Trying to get the value from the persistence for entity: ${entityID.getOrElse("not specified")}")

                val entityFromPostgres = cache.ask(new RetrieveFromPostgres(entityID, "entity_type"))

                entityFromPostgres.flatMap {
                  case future: Future[String] => future
                  case _ => Future.successful("Resource not found")
                }.recoverWith({
                  case ex:Exception=>{
                    logger.error(s"Recover after trying to get value from the cache with exception:${ex}")
                    Future.failed(ex.getCause)
                  }
                }).mapTo[String]
              }
            }
          }
        }
        }
    }
  }


  def getEntityID(entityID : Seq[JsValue]): Option[String] ={
    if(entityID.isEmpty || entityID.head.toString().replace("\"","").toLowerCase.equals("null"))
       None
    else
      Some(entityID.head.compactPrint)
  }

}


object StudentRecorder extends App with Service {

  Kamon.start()

  override implicit val system = ActorSystem()
  override implicit val executor = system.dispatcher

  override implicit val timeout = Timeout(30 seconds)

  new SchemaGeneration(
  url = "jdbc:postgresql://192.168.99.100:5432/postgres",
  user = "postgres",
  password = "postgres"
  ).run()
  implicit val materializer = ActorMaterializer(
    ActorMaterializerSettings(system)
      .withSupervisionStrategy(new RestartStreamStrategy)
  )

  override implicit val cache = system.actorOf(Props(new DBActorSupervisor()))
  override val config = ConfigFactory.load()
  override val logger = Logging(system, getClass)

  Http().bindAndHandle(endpoints, ConfigurationService.getHttpInterface, ConfigurationService.getHttpPort)
  override val connections = Tcp().bind(ConfigurationService.getSourceHostname, ConfigurationService.getSourcePort)

  connections runForeach { connection =>
    logger.debug(s"New connection from: ${connection.remoteAddress}")

    val responseFlow: Flow[ByteString, ByteString, NotUsed] = Flow[ByteString]
      .withAttributes(Attributes.inputBuffer(initial = 1, max = 1))
      .withAttributes(ActorAttributes.dispatcher("akka.stream.default-file-io-dispatcher"))
      .via(Framing.delimiter(
        ByteString(DELIMITER),
        maximumFrameLength = Int.MaxValue,
        allowTruncation = true))
      .map(x=>{
        logger.debug(s"we've got: ${x.takeRight(200).utf8String}")
        x.utf8String
      })
      .takeWhile(_!= "null")
      .map{result=>{
        val marketsParser = new EntityParser()
        marketsParser.parseEntityAndUpdateCache(result)
        result
        }
      }
      .map(_=>ByteString("B'Bye!"))

    connection.handleWith(responseFlow)
  }

  sys.addShutdownHook({
    Kamon.shutdown()
    system.terminate()
  })
}


