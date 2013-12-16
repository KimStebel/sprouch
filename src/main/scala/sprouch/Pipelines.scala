package sprouch

import akka.actor._
import akka.dispatch.Future
import spray.http._
import HttpMethods._
import spray.httpx.encoding.{Gzip, Deflate}
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling.Unmarshaller
import spray.json._
import spray.util._
import java.util.UUID
import akka.event.Logging
import sprouch.JsonProtocol.{ErrorResponseBody, ErrorResponse}
import sprouch.JsonProtocol.OkResponse
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.Writer
import spray.httpx.RequestBuilding.{Get => _, Delete => _, addCredentials => _, addHeader => _, _}
import spray.http._
import spray.client.pipelining._
import akka.dispatch.Await

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
    implicit val actorSystem:ActorSystem,
    hostName:String = "localhost",
    port:Int = 5984,
    userPass:Option[(String,String)] = None,
    https:Boolean = false
)

class Pipelines(config:Config) {
  import config.actorSystem
  
  //@volatile var docLogger:DocLogger = NopLogger
  
  @volatile var cookie:Option[String] = None
  
  implicit val timeout = akka.util.Timeout(akka.util.Duration("60 seconds"))
  
  /* TODO
  def cloudantLogin(docLogger:DocLogger = NopLogger):Future[String] = {
    import spray.http.MediaTypes.`application/x-www-form-urlencoded`
    val body = "name=" + userPass.get._1 + "&password=" + userPass.get._2
    val headers = List(
        "Accept" -> "*"+"/"+"*",
        "Content-Type" -> "application/x-www-form-urlencoded",
        "Content-Length" -> body.length.toString
    )
    val pl = pipelineWithoutUnmarshal(
        etag = None,
        useBasicAuth = false,
        additionalHeaders = headers,
        conduit = conduit,
        docLogger = docLogger)
    val respf = pl(HttpRequest(
        method = POST,
        uri = "/_session",
        entity = HttpEntity.apply(Some(HttpData.apply(   //TODO
            ContentType(`application/x-www-form-urlencoded`, None),
            body
        )))
    ))
    respf.map(resp => {
      val cookie = resp.headers.find(_.name == "Set-Cookie").get.value
      this.cookie = Some(cookie)
      cookie
    })
  }
  */
 
  def cloudantLogout(docLogger:DocLogger = NopLogger) = {
    val pl = pipeline[OkResponse](
        useBasicAuth = false,
        additionalHeaders = List("AuthSession" -> cookie.get),
        docLogger = docLogger)
    pl(Delete("/_session"))
  }
  def getAuthInfo(docLogger:DocLogger = NopLogger) = {
    val pl = pipeline[OkResponse](
        useBasicAuth = false,
        additionalHeaders = List("AuthSession" -> cookie.get),
        docLogger = docLogger)
    pl(Get("/_session"))
  }
  
  import akka.io.IO
  import akka.pattern.ask
  import spray.can.Http
  import spray.http._
  import spray.client.pipelining._

  import config.actorSystem.dispatcher // execution context for futures

  val conduit: ActorRef =
  Await.result((for (
    Http.HostConnectorInfo(connector, _) <-
      IO(Http) ? Http.HostConnectorSetup(config.hostName, port = config.port, sslEncryption = config.https)
  ) yield connector), akka.util.Duration("60 seconds"))
  
  
  
  private val log = Logging(actorSystem, conduit)
  
  private class MyBR(wr:Writer) extends BufferedWriter(wr) {
    override def close() {}
  }
  private def dl = new SphinxDocLogger((suffix, append) => {
    new MyBR(new OutputStreamWriter(System.out))
  })
  private val logRequest: HttpRequest => HttpRequest = r => {
    dl.logRequest(r)
    r
  }
  private val logResponse: HttpResponse => HttpResponse = r => {
    dl.logResponse(r)
    r
  }  
  import  spray.httpx.unmarshalling.FromResponseUnmarshaller
  def pipeline[A:FromResponseUnmarshaller]: HttpRequest => Future[A] = pipeline[A]()
  
  def unmarshalEither[A:FromResponseUnmarshaller]: HttpResponse => A = {
      hr => (hr match {
        case HttpResponse(status, _, _, _) if status.value == 304 => {//not modified
          throw new SprouchException(ErrorResponse(status.intValue, None))
        }
        case HttpResponse(status, _, _, _) if status.isSuccess => {
          unmarshal[A](implicitly[FromResponseUnmarshaller[A]])(hr)
        }
        case HttpResponse(errorStatus, _, _, _) => {
          log.error(hr.toString)
          val ue = implicitly[FromResponseUnmarshaller[ErrorResponseBody]]
          val body = unmarshal[ErrorResponseBody](ue)(hr.copy(status = StatusCodes.OK))
          throw new SprouchException(ErrorResponse(errorStatus.intValue, Option(body)))
        }
      })
    }
    
  
  def pipeline[A:FromResponseUnmarshaller](
      etag:Option[String] = None,
      useBasicAuth:Boolean = true,
      additionalHeaders:List[(String,String)] = Nil,
      conduit:ActorRef = this.conduit,
      docLogger:DocLogger = NopLogger
  ): HttpRequest => Future[A] = {
    pipelineWithoutUnmarshal(etag, useBasicAuth, additionalHeaders, conduit, docLogger) ~>
    unmarshalEither[A]
  }
  
  def addBasicAuth(req:HttpRequest) = (config.userPass match {
    case Some((u,p)) => addCredentials(BasicHttpCredentials(u, p))
    case None => (x:HttpRequest) => x
  })(req)
  
  def pipelineWithoutUnmarshal(
      etag:Option[String] = None,
      useBasicAuth:Boolean = true,
      additionalHeaders:List[(String,String)] = Nil,
      conduit:ActorRef = this.conduit,
      docLogger:DocLogger = NopLogger): HttpRequest => Future[HttpResponse] = {
    (if (additionalHeaders.exists(s => (s._1.toLowerCase == "accept"))) {
      identity[HttpRequest] _
    } else {
      addHeader("accept", "application/json")
    }) ~>
    (etag match {
      case Some(etag) => addHeader("If-None-Match", "\"" + etag + "\"") 
      case None => (x:HttpRequest) => x
    }) ~>
    (if (useBasicAuth) (addBasicAuth _) else identity[HttpRequest] _) ~>
    {
        additionalHeaders.map { 
          case (k,v) => addHeader(k,v)
        }.foldRight(identity[HttpRequest] _) {
          (f1,f2) => f1 andThen f2
        }
    } ~>
    ((hr:HttpRequest) => {println(hr); hr}) ~>
    ((hr:HttpRequest) => {docLogger.logRequest(hr); hr}) ~>
    sendReceive(conduit) ~>
    ((hr:HttpResponse) => {docLogger.logResponse(hr); hr}) ~>
    ((hr:HttpResponse) => {/*println(hr);*/ hr})
    
  }
  
}
