package sprouch

import spray.http.HttpRequest
import spray.http.HttpResponse
import java.io._
import spray.http.HttpMessage
import spray.json._
import akka.actor.ActorRef
import akka.actor.Actor
import spray.http.ChunkedResponseStart
import spray.http.ChunkedMessageEnd
import spray.http.MessageChunk
import akka.event.Logging
import spray.http.HttpResponsePart
import ChunkedResponseLoggerActor._
import spray.http.HttpHeader

trait DocLogger {
  def logRequest(request:HttpRequest):Unit
  def logResponse(response:HttpResponse):Unit
  
}

object SphinxDocLogger {
  def apply(fileName:String) = {
    val baseName = System.getenv("TESTY_RESULT_DIR") + "/" + fileName
    new SphinxDocLogger(baseName)
  }
}

object ChunkedResponseLoggerActor {
  sealed trait HoB
  sealed trait RoR
  case object headers extends HoB
  case object body extends HoB
  case object request extends RoR
  case object response extends RoR

  sealed trait HttpEvent {
    def description: String
  }

  case class ResponseHeaders(description:String, resp:HttpResponse) extends HttpEvent
  case class ResponseBodyJson(description:String, body:JsValue) extends HttpEvent
  case class ResponseBodyString(description:String, body:String) extends HttpEvent
  case class RequestHeaders(description:String, headers:HttpRequest) extends HttpEvent
  case class RequestBodyJson(description:String, body:JsValue) extends HttpEvent
  case class RequestBodyString(description:String, body:String) extends HttpEvent
}

class ChunkedResponseLoggerActor extends Actor {
  var acc = List[String]()
  val log = Logging(context.system, this)
  
  def receive = {
    case e: HttpEvent => {
      val dl = SphinxDocLogger(e.description)
      e match {
        case  ResponseHeaders(_, resp) => {
          dl.logResponseHeaders(resp)
        }
        case  ResponseBodyJson(_, b) => {
          dl.logResponseBodyJson(b)
        }
        case  ResponseBodyString(_, b) => {
          dl.logResponseBodyString(b)
        }
        case  RequestHeaders(_, req) => {
          dl.logRequestHeaders(req)
        }
        case  RequestBodyJson(_, b) => {
          
        }
        case  RequestBodyString(_, b) => {
          
        }
      }
    }
  }
}
                             //suffix,append
class SphinxDocLogger(getOut: (String,Boolean)=>BufferedWriter) extends DocLogger {
  private def this(fileName:String) {
    this((suffix,append) => 
      new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName+suffix, append)))
    )
  }
  
  def logRequestHeaders(req:HttpRequest) = {
    withWriter(request, headers, false)(out => {
      logRequestStart(req, out)
      logHeaders(req.headers, out)
    })
    withWriter(request, body, false)(logBodyStart)
  }
  
  def logResponseHeaders(resp:HttpResponse) = {
    withWriter(response, headers, false)(out => {
      logResponseStart(out, resp)
      logHeaders(resp.headers, out)
    })
    withWriter(response, body, false)(logBodyStart)
  }
  
  def logResponseBodyJson(js:JsValue) {
    withWriter(response, body, true)(out => {
      logBodyPartJson(js, out)
    })
  }
  def logResponseBodyString(str:String) {
    withWriter(response, body, true)(out => {
      logBodyPartString(str, out)
    })
  }
  
  
  private def logHeaders(hs:Seq[HttpHeader], out:BufferedWriter) {
    hs.filter(_.name != "Authorization").foreach(h => {
      out.write("    " + h.name + ": " + h.value)
      out.newLine()
    })
    out.newLine()
  }
  
  def logBody(bodyStr:String, out:BufferedWriter) {
    try {
      logBodyPartJson(bodyStr.asJson, out)
    } catch {
      case e: Throwable => { //parsing exception, so not json
        logBodyPartString(bodyStr, out)
      }
    }
  }
  
  def withWriter(reqOrResp:RoR, headersOrBody:HoB, append:Boolean)(f:BufferedWriter=>Unit):Unit = {
    def withWriter(suffix:String, append:Boolean)(f:BufferedWriter=>Unit):Unit = {
      val out = getOut(suffix,append)
      try {
        f(out)
        out.flush()
      } finally {
        out.close()  
      }
    }
    withWriter("-" + reqOrResp + "-" + headersOrBody + ".inc", append)(f)
  }
  
  def logBodyStart(out:BufferedWriter) {
    out.write(".. code-block:: javascript")
    out.newLine(); out.newLine()
  }
  
  def logBodyPartJson(body:JsValue, out:BufferedWriter) {
    logBodyPartString(body.prettyPrint.split("\\n").map("    " +).mkString("\n"), out)
  }
  
  def logBodyPartString(bodyStr:String, out:BufferedWriter) {
    val entityStr = bodyStr.split("\\n").map("    " +).mkString("\n")
    out.write(entityStr)
    out.newLine()
  }
  
  private def logRequestStart(req:HttpRequest, out:BufferedWriter) {
    out.write(".. code-block:: http")
    out.newLine(); out.newLine()
    def prettyUri(uri:String) = {
      if (uri.startsWith("/_")) uri else {
        val parts = uri.split("/").toList
        val replacedDbName = "db" :: parts.tail.tail
        "/" + replacedDbName.mkString("/")
      }
    }
    out.write("    " + req.method + " " + prettyUri(req.uri.toRelative.toString) + " " + req.protocol)
    out.newLine()
  }
  
  def logRequest(req:HttpRequest) = {
    withWriter(request, headers, false)(out => {
      logRequestStart(req, out)
      logHeaders(req.headers, out)
    })
    withWriter(request, body, false)(out => {
      logBodyStart(out)
      logBody(req.entity.asString, out)
    })
  }
  
  private def logResponseStart(out:BufferedWriter, resp:HttpResponse) = {
    out.write(".. code-block:: http")
    out.newLine(); out.newLine()
    out.write("    " + resp.protocol.value + " " + resp.status.value)
    out.newLine()
    
  }
  def logResponse(resp:HttpResponse) = {
    withWriter(response, headers, false)(out => {
      logResponseStart(out, resp)
      logHeaders(resp.headers, out)
    })
    withWriter(response, body, false)(out => {
      logBodyStart(out)
      logBody(resp.entity.asString, out)
    })
  }
}

object NopLogger extends DocLogger {
  def logRequest(req:HttpRequest) {}
  def logResponse(resp:HttpResponse) {}
  def logBody(reqOrResp:String, body:Seq[String]) {}
}