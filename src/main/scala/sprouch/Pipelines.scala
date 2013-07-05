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
import spray.json._
import spray.util._
import java.util.UUID
import akka.event.Logging
import sprouch.JsonProtocol.{ErrorResponseBody, ErrorResponse}
import spray.io.{IOBridge, IOExtension}
import sprouch.JsonProtocol.OkResponse
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.Writer

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

class Pipelines(config:Config) {
  import config._
  
  //@volatile var docLogger:DocLogger = NopLogger
  
  @volatile var cookie:Option[String] = None
  
  def cloudantLogin(docLogger:DocLogger = NopLogger):Future[String] = {
    import spray.http.MediaTypes.`application/x-www-form-urlencoded`
    val body = "name=" + userPass.get._1 + "&password=" + userPass.get._2
    val headers = List(
        "Accept" -> "*/*",
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
        entity = HttpEntity(Some(HttpBody(
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
  
  private val conduit = {
    val ioBridge = IOExtension(actorSystem).ioBridge()
    val httpClient = actorSystem.actorOf(Props(new HttpClient(ioBridge)))
    actorSystem.actorOf(Props(new HttpConduit(httpClient, hostName, port, https)))
  }
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
  def pipeline[A:Unmarshaller]: HttpRequest => Future[A] = pipeline[A]()
  def pipeline[A:Unmarshaller](
      etag:Option[String] = None,
      useBasicAuth:Boolean = true,
      additionalHeaders:List[(String,String)] = Nil,
      conduit:ActorRef = this.conduit,
      docLogger:DocLogger = NopLogger
  ): HttpRequest => Future[A] = {
    def unmarshalEither[A:Unmarshaller]: HttpResponse => A = {
      hr => (hr match {
        case HttpResponse(status, _, _, _) if status.value == 304 => {//not modified
          throw new SprouchException(ErrorResponse(status.value, None))
        }
        case HttpResponse(status, _, _, _) if status.isSuccess => {
          unmarshal[A](implicitly[Unmarshaller[A]])(hr)
        }
        case HttpResponse(errorStatus, _, _, _) => {
          log.error(hr.toString)
          val ue = implicitly[Unmarshaller[ErrorResponseBody]]
          val body = unmarshal[ErrorResponseBody](ue)(hr.copy(status = StatusCodes.OK))
          throw new SprouchException(ErrorResponse(errorStatus.value, Option(body)))
        }
      })
    }
    pipelineWithoutUnmarshal(etag, useBasicAuth, additionalHeaders, conduit, docLogger) ~>
    unmarshalEither[A]
  }
  
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
    (userPass.filter(_ => useBasicAuth) match {
      case Some((u,p)) => addCredentials(BasicHttpCredentials(u, p))
      case None => (x:HttpRequest) => x
    }) ~>
    {
        additionalHeaders.map { 
          case (k,v) => addHeader(k,v)
        }.foldRight(identity[HttpRequest] _) {
          (f1,f2) => f1 andThen f2
        }
    } ~>
    ((r:HttpRequest) => { docLogger.logRequest(r); r }) ~>
    //logRequest ~>
    sendReceive(conduit) ~>
    //logResponse ~>
    ((r:HttpResponse) => { docLogger.logResponse(r); r })
  }
  
}
