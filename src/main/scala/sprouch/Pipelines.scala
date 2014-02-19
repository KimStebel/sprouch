package sprouch

import akka.util.Timeout
import akka.actor._
import scala.concurrent.Future
import spray.client.pipelining
import spray.client.pipelining._
import spray.http._
import HttpMethods._
import spray.httpx.encoding.{Gzip, Deflate}
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling.FromResponseUnmarshaller
import spray.json._
import spray.util._
import java.util.UUID
import akka.event.Logging
import akka.actor.Actor
import akka.actor.Props
import akka.event.Logging
import sprouch.JsonProtocol.ErrorResponseBody
import sprouch.JsonProtocol.ErrorResponse
import akka.io.IO
import spray.can.Http
import akka.pattern.ask
import spray.io.{ ConnectionTimeouts, ClientSSLEngineProvider, ServerSSLEngineProvider }
import scala.concurrent.duration.Duration
import scala.concurrent.Await
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
  implicit val system = actorSystem
  implicit val timeout = Timeout(10000)
  import system.dispatcher
  
  def transportActorRefFuture = for (
      Http.HostConnectorInfo(connector, _) <- IO(Http)(actorSystem) ? Http.HostConnectorSetup(host = hostName, port = port, sslEncryption = https)
  ) yield connector 
  
  def pipeline[A:FromResponseUnmarshaller]: HttpRequest => Future[A] = pipeline[A](None)
  
  def pipeline[A:FromResponseUnmarshaller](etag:Option[String]): HttpRequest => Future[A] = {
    def unmarshalEither[A:FromResponseUnmarshaller]: HttpResponse => A = {
      hr => (hr match {
        case HttpResponse(status, _, _, _) if status.intValue == 304 => {//not modified
          throw new SprouchException(ErrorResponse(status.intValue, None))
        }
        case HttpResponse(status, _, _, _) if status.isSuccess => {
          unmarshal[A](implicitly[FromResponseUnmarshaller[A]])(hr)
        }
        case HttpResponse(errorStatus, _, _, _) => {
          val ue = implicitly[FromResponseUnmarshaller[ErrorResponseBody]]
          val body = unmarshal[ErrorResponseBody](ue)(hr.copy(status = StatusCodes.OK))
          throw new SprouchException(ErrorResponse(errorStatus.intValue, Option(body)))
        }
      })
    }
    val transportActorRef = Await.result[ActorRef](transportActorRefFuture, Duration("10 seconds"))
    
    addHeader("accept", "application/json") ~>
    (etag match {
      case Some(etag) => addHeader("If-None-Match", "\"" + etag + "\"") 
      case None => (x:HttpRequest) => x
    }) ~>
    (userPass match {
      case Some((u,p)) => addCredentials(BasicHttpCredentials(u, p))
      case None => (x:HttpRequest) => x
    }) ~>
    sendReceive(transportActorRef) ~>
    unmarshalEither[A]
  }
  
}
