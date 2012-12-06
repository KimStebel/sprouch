package sprouch

import akka.actor._
import akka.dispatch.Future
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

case class Config(
    actorSystem:ActorSystem,
    hostName:String = "localhost",
    port:Int = 5984,
    userPass:Option[(String,String)] = None,
    https:Boolean = false)

private class Pipelines(config:Config) {
  import config._
  
  private val conduit = {
    val ioBridge = new IOBridge(actorSystem).start()
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
  
  def pipeline[A,B](implicit ua: Unmarshaller[A], ub: Unmarshaller[B]): HttpRequest => Future[Either[A,B]] = {
    def unmarshalEither[A,B](hr:HttpResponse)(implicit ua: Unmarshaller[A], ub: Unmarshaller[B]):Either[A,B] =
      hr match {
        case HttpResponse(status, _, _, _) if status.isSuccess => {
          Right(unmarshal[B](ub)(hr))
        }
        case HttpResponse(_, _, _, _) => {
          log.error(hr.toString)
          Left(unmarshal[A](ua)(hr.copy(status = StatusCodes.OK)))
        }
        
      }
  
    addHeader("accept", "application/json") ~>
    (userPass match {
      case Some((u,p)) => addCredentials(BasicHttpCredentials(u, p))
      case None => (x:HttpRequest) => x
    }) ~>
    //logRequest ~> 
    sendReceive(conduit) ~>
    //logResponse ~>
    unmarshalEither[A,B]
  }
}
