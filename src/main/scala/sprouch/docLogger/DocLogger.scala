package sprouch.docLogger

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
import spray.http.Uri.Path

trait DocLogger {
  def logRequest(request:HttpRequest):Unit
  def logResponse(response:HttpResponse):Unit
}

object MdDocLogger {
  def apply(fileName:String) = {
    val baseName = System.getenv("TESTY_RESULT_DIR") + "/" + fileName
    new MdDocLogger(baseName)
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
    def description:String
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
    case e:HttpEvent => {
      val dl = MdDocLogger(e.description)
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
class MdDocLogger(getOut: (String,Boolean)=>BufferedWriter) extends DocLogger {
  val languages = Map(
    "python" -> new PythonGenerator,
    "javascript" -> new JsGenerator,
    "bash" -> new CurlGenerator
  )
  
  private def writeCode(request:HttpRequest) {
    for ((language,generator) <- languages) {
      storeDoc(language, generator.generateCode(request))
    }
  }
  private def storeDoc(language:String, code:Seq[String]) {
    withWriter("-" + language + ".md", false)(writer =>{
      writer.write("```" + language)
      writer.newLine()
      for (line <- code) {
        writer.write("    " + line)
        writer.newLine()
      }
      writer.write("```")
      writer.newLine()  
    })  
  }
  
  private def this(fileName:String) {
    this((suffix,append) => 
      new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName+suffix, append)))
    )
  }
  
  private[docLogger] def logRequestHeaders(req:HttpRequest) = {
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
    hs.foreach(h => {
      out.write("    " + h.name + ": " + h.value)
      out.newLine()
    })
    if (!md) { out.newLine() }
  }
  
  def logBody(bodyStr:String, out:BufferedWriter) {
    try {
      logBodyPartJson(bodyStr.asJson, out)
    } catch {
      case e => { //parsing exception, so not json
        logBodyPartString(bodyStr, out)
      }
    }
  }
  
  def withWriter(suffix:String, append:Boolean)(f:BufferedWriter=>Unit):Unit = {
    val out = getOut(suffix,append)
    try {
      f(out)
      out.flush()
    } finally {
      out.close()
    }
  }
  
  def withWriter(reqOrResp:RoR, headersOrBody:HoB, append:Boolean)(f:BufferedWriter=>Unit):Unit = {
    withWriter("-" + reqOrResp + "-" + headersOrBody + (if (md) ".md" else ".inc"), append)(f)
  }
  
  def logBodyStart(out:BufferedWriter) {
    out.write(codeBlockStart("javascript"))
    out.newLine(); if (!md) { out.newLine() }
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
    out.write(codeBlockStart("http"))
    out.newLine(); if (!md) { out.newLine() }
    out.write("    " + req.method + " " + req.uri.toRelative.toString + " " + req.protocol)
    out.newLine()
  }
  private def prettyPath(p:Path):Path = {
    if (p.head.toString.startsWith("_")) p else (Path./("db") ++ p.tail.tail)
  }
  
  def logRequest(req:HttpRequest) = {
    val redactedReq = HttpRequest(req.method, req.uri.withPath(prettyPath(req.uri.path)), req.headers.filter(_.name.toLowerCase != "authorization"), req.entity, req.protocol)
    writeCode(redactedReq)
    withWriter(request, headers, false)(out => {
      logRequestStart(redactedReq, out)
      logHeaders(redactedReq.headers, out)
      out.write("```")
      out.newLine()
    })
    withWriter(request, body, false)(out => {
      logBodyStart(out)
      logBody(redactedReq.entity.asString, out)
      out.write("```")
      out.newLine()
    })
  }
  val md = true
  def codeBlockStart(language:String) = if (md) "```" + language else ".. code-block:: " + language
  private def logResponseStart(out:BufferedWriter, resp:HttpResponse) = {
    out.write(codeBlockStart("http"))
    out.newLine(); if (!md) { out.newLine() }
    out.write("    " + resp.protocol.value + " " + resp.status.value)
    out.newLine()
    
  }
  def logResponse(resp:HttpResponse) = {
    withWriter(response, headers, false)(out => {
      logResponseStart(out, resp)
      logHeaders(resp.headers, out)
      out.write("```")
      out.newLine()
    })
    withWriter(response, body, false)(out => {
      logBodyStart(out)
      logBody(resp.entity.asString, out)
      out.write("```")
      out.newLine()
    })
  }
}

object NopLogger extends DocLogger {
  def logRequest(req:HttpRequest) {}
  def logResponse(resp:HttpResponse) {}  
}