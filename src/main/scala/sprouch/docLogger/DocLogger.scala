package sprouch.docLogger

import spray.http.{HttpHeader, HttpResponsePart, HttpMessage, HttpRequest, HttpResponse, ChunkedResponseStart, ChunkedMessageEnd, MessageChunk}
import java.io._
import spray.json._
import akka.actor.{ActorRef, Actor}
import akka.event.Logging
import ChunkedResponseLoggerActor._
import spray.http.Uri.Path
import sprouch.JsonProtocol._

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
  def logResponse(resp:HttpResponse) = {
    withJsonWriter(response, headers, false)(out => {
      logResponseStart(out, resp)
      logHeaders(resp.headers, out)
      out.write(codeBlockEnd)
      out.newLine()
    })
    withJsonWriter(response, body, false)(out => {
      logBodyStart(out)
      logBody(resp.entity.asString, out)
      out.write(codeBlockEnd)
      out.newLine()
    })
  }
  
  def logRequest(req:HttpRequest) = {
    val redactedReq = HttpRequest(req.method, req.uri.withPath(prettyPath(req.uri.path)), req.headers.filter(_.name.toLowerCase != "authorization"), req.entity, req.protocol)
    writeCode(redactedReq)
    withJsonWriter(request, headers, false)(out => {
      logRequestStart(redactedReq, out)
      logHeaders(redactedReq.headers, out)
      out.write(codeBlockEnd)
      out.newLine()
    })
    withJsonWriter(request, body, false)(out => {
      logBodyStart(out)
      logBody(redactedReq.entity.asString, out)
      out.write(codeBlockEnd)
      out.newLine()
    })
  }
  
  
  //end public api
  
  private[docLogger] def logRequestHeaders(req:HttpRequest) = {
    withWriter(request, headers, false)(out => {
      logRequestStart(req, out)
      logHeaders(req.headers, out)
    })
    withWriter(request, body, false)(logBodyStart)
  }
  
  private[docLogger] def logResponseHeaders(resp:HttpResponse) = {
    withWriter(response, headers, false)(out => {
      logResponseStart(out, resp)
      logHeaders(resp.headers, out)
    })
    withWriter(response, body, false)(logBodyStart)
  }
  
  private[docLogger] def logResponseBodyJson(js:JsValue) {
    withWriter(response, body, true)(out => {
      logBodyPartJson(js, out)
    })
  }
  private[docLogger] def logResponseBodyString(str:String) {
    withWriter(response, body, true)(out => {
      logBodyPartString(str, out)
    })
  }
  //end package private api
  
  //code generation and pretty printing
  
  private val languages = Map(
    "python" -> new PythonGenerator,
    "javascript" -> new JsGenerator,
    "bash" -> new CurlGenerator/*,
    "java" -> new JavaGenerator*/
  )
  
  private def prettyPath(p:Path):Path = {
    if (p.toString.startsWith("/_")) p else (Path./("db") ++ p.tail.tail)
  }
  
  private lazy val codeBlockEnd = if (md) "```" else ""
  
  //file IO
  
  private lazy val filenameSuffix = if (md) ".md" else ".inc"
  
  private def withWriter(suffix:String, append:Boolean)(f:BufferedWriter=>Unit):Unit = {
    val out = getOut(suffix,append)
    try {
      f(out)
      out.flush()
    } finally {
      out.close()
    }
  }
  
  private def withWriter(reqOrResp:RoR, headersOrBody:HoB, append:Boolean)(f:BufferedWriter=>Unit):Unit = {
    withWriter("_code_http_" + reqOrResp + "_" + headersOrBody + filenameSuffix, append)(f)
  }
  private def withJsonWriter(suffix:String, append:Boolean)(f:BufferedWriter=>Unit):Unit = {
    val out = getOut(suffix,append)
    try {
      val sw = new StringWriter
      val mdWriter = new BufferedWriter(sw)
      f(mdWriter)
      mdWriter.flush()
      val s = sw.toString()
      val sample = CodeSample(language = "http", text = s, `type` = "code")
      val output = implicitly[JsonWriter[CodeSample]].write(sample).prettyPrint
      out.write(output)
      out.flush()
    } finally {
      out.close()
    }
  }
  
  private def withJsonWriter(reqOrResp:RoR, headersOrBody:HoB, append:Boolean)(f:BufferedWriter=>Unit):Unit = {
    withJsonWriter("_code_http_" + reqOrResp + "_" + headersOrBody + filenameSuffix, append)(f)
  }
  
  //file IO ends
  
  private def writeCode(request:HttpRequest) {
    for ((language,generator) <- languages) {
      storeDoc(language, generator.generateCode(request))
    }
  }

  private def storeDoc(language:String, code:Seq[String]) {
    withWriter("_code_" + language + filenameSuffix, false)(writer => {
      val sw = new StringWriter
      val mdWriter = new BufferedWriter(sw)
      mdWriter.write(codeBlockStart(language))
      mdWriter.newLine()
      for (line <- code) {
        mdWriter.write("    " + line)
        mdWriter.newLine()
      }
      mdWriter.write(codeBlockEnd)
      mdWriter.newLine()  
      mdWriter.flush()
      val s = sw.toString()
      val sample = CodeSample(language = language, text = s, `type` = "code")
      val output = implicitly[JsonWriter[CodeSample]].write(sample).prettyPrint
      writer.write(output)
    })
  }
  
  private def this(fileName:String) {
    this((suffix,append) => 
      new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName+suffix, append)))
    )
  }
  
  private def logHeaders(hs:Seq[HttpHeader], out:BufferedWriter) {
    hs.foreach(h => {
      out.write("    " + h.name + ": " + h.value)
      out.newLine()
    })
    if (!md) { out.newLine() }
  }
  
  private def logBody(bodyStr:String, out:BufferedWriter) {
    try {
      logBodyPartJson(bodyStr.asJson, out)
    } catch {
      case e => { //parsing exception, so not json
        logBodyPartString(bodyStr, out)
      }
    }
  }
  
  private def logBodyStart(out:BufferedWriter) {
    out.write(codeBlockStart("javascript"))
    out.newLine();
  }
  
  private def logBodyPartJson(body:JsValue, out:BufferedWriter) {
    logBodyPartString(body.prettyPrint.split("\\n").map("    " +).mkString("\n"), out)
  }
  
  private def logBodyPartString(bodyStr:String, out:BufferedWriter) {
    val entityStr = bodyStr.split("\\n").map("    " +).mkString("\n")
    out.write(entityStr)
    out.newLine()
  }
    
  private def logRequestStart(req:HttpRequest, out:BufferedWriter) {
    out.write(codeBlockStart("http"))
    out.newLine();
    out.write("    " + req.method + " " + req.uri.toRelative.toString + " " + req.protocol)
    out.newLine()
  }
  
  private lazy val md = Seq("md", "markdown").contains(Option(System.getenv("SPROUCH_DOCLOGGER_OUTPUT_FORMAT")).getOrElse("").toLowerCase)
  private def codeBlockStart(language:String) = {
    if (md) {
      "```" + language
    } else {
      ".. code-block:: " + language + "\n"
    }
  }
  private def logResponseStart(out:BufferedWriter, resp:HttpResponse) = {
    out.write(codeBlockStart("http"))
    out.newLine();
    out.write("    " + resp.protocol.value + " " + resp.status.value)
    out.newLine() 
  }
}

object NopLogger extends DocLogger {
  def logRequest(req:HttpRequest) {}
  def logResponse(resp:HttpResponse) {}  
}