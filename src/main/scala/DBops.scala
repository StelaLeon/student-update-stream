import java.net.ConnectException
import java.util.UUID
import java.util.concurrent.{Executors, Executor}

import akka.actor.{Actor, ActorSystem, Props}
import akka.pattern.{Backoff, BackoffSupervisor, CircuitBreaker, ask}
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import persistency.PersistenceModuleImpl

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import persistency.{Student, PersistenceModuleImpl}

/**
 * Created by sleon on 8/17/16.
 */


//TODO make this class more generic using type classes
//com.redis.serialization.Parse[B] needs to be defined when getting the value from the cache as type B
case class UpdatePostgres(id: Option[String], resourceType: String, value:String)
case class RetrieveFromPostgres(id: Option[String], resouceType: String)

class DBActorSupervisor(implicit system:ActorSystem, timeout: Timeout) extends Actor with LazyLogging {
  val childProps = Props(classOf[DBOps])

  var persistenceClient = system.actorOf(childProps)

    val supervisor = BackoffSupervisor.props(
      Backoff.onStop(
        childProps,
        childName = "PersistenceDB",
        minBackoff = 3.seconds,
        maxBackoff = 30.seconds,
        randomFactor = 0.2 // adds 20% "noise" to vary the intervals slightly
      ))

    system.actorOf(supervisor, name = "PersistenceDB")



  override def receive: Actor.Receive = {
    case UpdatePostgres(id,resourceType, value) => {
      try{
        logger.info(s"[DBActorSupervisor] got an update cache message for ${id}, $resourceType")
        persistenceClient ! UpdatePostgres(id,resourceType, value)

      }catch{
        case ex:Exception=>logger.error(s"[DBActorSupervisor] Updating redis cache failed with: ${ex}")
      }
    }
    case RetrieveFromPostgres(id, resourceType: String) => {
      try {
        logger.info(s"[DBActorSupervisor] retrieving key from cache for:${id}, $resourceType" )
        val resultFromCache = persistenceClient.ask(new RetrieveFromPostgres(id, resourceType: String)).flatMap {
          case future: Future[String] => future
          case _ => Future.successful("Resource not found in the cache")
        }.mapTo[String]
        logger.info(s"[DBActorSupervisor] got a value from cahce ${resultFromCache}")
        sender() ! resultFromCache
      }catch {
        case ex:Exception=> logger.error(s" [DBActorSupervisor] Retrieving value from redis cache failed with ${ex}")
          sender() ! akka.actor.Status.Success
      }
    }
  }
}

class DBOps extends Actor with LazyLogging with PersistenceModuleImpl{

  val breaker =
    new CircuitBreaker(
      context.system.scheduler,
      maxFailures = 7,
      callTimeout = 10.seconds,
      resetTimeout = 1.minute).onOpen(notifyMeOnOpen())


  def notifyMeOnOpen(): Unit =
    logger.debug("[DBOps] CircuitBreaker is now open, and will not close for one minute")


  def updateCache(id: Option[String], resourceType: String, value:String) ={
    logger.info(s"[DBOps] Trying to update Database with resourceType ${resourceType}")
    try{
      val dbEntry = Student(id.getOrElse(UUID.randomUUID().toString),resourceType,"state",value)
      suppliersDal.insert(dbEntry)
    }catch{
      case ex:ConnectException => logger.error(s"[DBOps] While trying to update the cache we noticed Redis cache is down with exception: ${ex}")
      case e:Exception =>logger.error(s"[DBOps] A problem occured, we encounter: ${e}")
    }
  }

  def getValueFromCache(id:Option[String], resourceType: String): Future[String]={
    logger.info(s"[DBOps] Trying to get value from cache with resourceType ${resourceType}")
    try{
      id match{
        case Some(x)=> suppliersDal.findById(id.get).flatMap(res=>Future{res match{
          case Some(v)=> v.resource_info
          case _ => "Resource not found"
        }})
        case None => suppliersDal.findByType(resourceType).flatMap(res=>Future{res match{
          case Some(v)=> v.resource_info
          case _ => "Resource not found"
        }})
      }
    }catch{
      case ex:ConnectException=>{
        logger.error(s"[DBOps] While trying to update the cache we noticed Redis cache is down with exception: ${ex}")
        Future{s"The DB is down with exception ${ex.getMessage}"}
      }
      case e: Exception => {
        logger.error(s"[DBOps] A problem occured, we encounter: ${e}")
        Future{s"The DB is down with exception ${e.getMessage}"}
      }
    }
  }

  override def receive: Receive = {
    case UpdatePostgres(id, resourceType, value) => {
      breaker.withCircuitBreaker(Future(updateCache(id, resourceType, value)))
    }
    case RetrieveFromPostgres(id, resouceType) => {
      sender() ! breaker.withCircuitBreaker(getValueFromCache(id, resouceType))
    }
  }
}


