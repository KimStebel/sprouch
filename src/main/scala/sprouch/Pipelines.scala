package sprouch

import akka.actor._
import spray.can.client.HttpClient
import spray.client.HttpConduit
import HttpConduit._
import spray.http._
import HttpMethods._
import spray.httpx.encoding.{Gzip, Deflate}
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling.Unmarshaller
import spray.io._
import spray.json._
import spray.util._
import java.util.UUID
import akka.event.Logging
import akka.actor.Actor
import akka.actor.Props
import akka.event.Logging
import sprouch.JsonProtocol.ErrorResponseBody
import sprouch.JsonProtocol.ErrorResponse
import scala.concurrent.Future

/**
 * Configuration data, default values should be valid for a default install of CouchDB.
 * 
 * @constructor
 * 
 * @param userPass Optional pair of username and password.
 * @param https Whether to use https. If true, the config property spray.can.client.ssl-encryption
 *  must be set to on, which is the default setting in the reference.conf of this library.
 */
case class Config(
    actorSystem:ActorSystem,
    hostName:String = "localhost",
    port:Int = 5984,
    userPass:Option[(String,String)] = None,
    https:Boolean = false
)

private[sprouch] class Pipelines(config:Config) {
  import config._
  
  val as = config.actorSystem
  import as.dispatcher
	
  private val conduit = {
    val ioBridge = IOExtension(actorSystem).ioBridge
    val httpClient = actorSystem.actorOf(Props(new HttpClient(ioBridge)))
    actorSystem.actorOf(Props(new HttpConduit(httpClient, hostName, port, https)))
  }
  private val log = Logging(actorSystem, conduit)
  
  private val logRequest: HttpRequest => HttpRequest = r => {
    log.info(r.toString)
    r
  }
  
  private val logResponse: HttpResponse => HttpResponse = r => {
    log.info(r.toString)
    r
  }  
  
  def pipeline[A:Unmarshaller]: HttpRequest => Future[A] = {
  	
  	def unmarshalEither[A:Unmarshaller](hr:HttpResponse):A = {
      (hr match {
        case HttpResponse(status, _, _, _) if status.isSuccess => {
          Right(unmarshal[A](implicitly[Unmarshaller[A]])(hr))
        }
        case HttpResponse(errorStatus, _, _, _) => {
          log.error(hr.toString)
          val ue = implicitly[Unmarshaller[ErrorResponseBody]]
          val body = unmarshal[ErrorResponseBody](ue)(hr.copy(status = StatusCodes.OK))
          Left(ErrorResponse(errorStatus.value, body))
        }
        
      }) match {
        case Left(e) => throw new SprouchException(e)
        case Right(r) => r
      }
    }
    
    val p: HttpRequest => Future[HttpResponse] = {
      (addHeader("accept", "application/json") ~>
      (userPass match {
        case Some((u,p)) => addCredentials(BasicHttpCredentials(u, p))
        case None => (x:HttpRequest) => x
      }) ~>
      sendReceive(conduit))
    }
    p.andThen(resp => resp.map(unmarshalEither[A]))
  }
}
