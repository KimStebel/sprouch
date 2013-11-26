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

trait DocLogger {
  def logRequest(request:HttpRequest):Unit
  def logResponse(response:HttpResponse):Unit
  def logBody(reqOrResp:String, body:Seq[String]):Unit
}

object SphinxDocLogger {
  def apply(fileName:String) = {
    new SphinxDocLogger(System.getenv("TESTY_RESULT_DIR") + "/" + fileName)
  }
}

class ChunkedResponseLoggerActor(actor:Actor, dl:DocLogger) extends Actor {
  var acc = List[String]()
  val log = Logging(context.system, this)
  
  def receive = {
    case crs:ChunkedResponseStart => {
      log.info("crs received")
      dl.logResponse(crs.message)
      actor.receive(crs)
      actor.forward(crs)
    }
    case cme:ChunkedMessageEnd => {
      dl.logBody("response", acc.reverse)
      actor.forward(cme)
    }
    case mc:MessageChunk => {
      acc ::= mc.data.asString
      actor.forward(mc)
    }
    case other => actor.forward(other)
  }
}

class SphinxDocLogger(getOut: (String,Boolean)=>BufferedWriter) extends DocLogger {
  private def this(fileName:String) {
    this((suffix,append) => 
      new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName+suffix, append)))
    )
  }
  
  private def withWriter(suffix:String, append:Boolean=false)(f:BufferedWriter=>Unit) = {
    val out = getOut(suffix,append)
    try {
      f(out)
      out.flush()
    } finally {
      out.close()  
    }
  }
  
  private def logMessage(out:BufferedWriter, m:HttpMessage) {
    m.headers.filter(_.name != "Authorization").foreach(h => {
      out.write("    " + h.name + ": " + h.value)
      out.newLine()
    })
    out.newLine()
    out.flush
    if (!m.entity.isEmpty) {
      val reqResp = m match {
        case _:HttpRequest => "request"
        case _:HttpResponse => "response"
      }
      logBody(reqResp, Seq(m.entity.asString))
    }
  }
  
  def logBody(reqOrResp:String, body:Seq[String]) {
    withWriter("-" + reqOrResp + "-body.inc")(out => {
        out.write(".. code-block:: javascript")
        out.newLine(); out.newLine()
        val entityStr = 
          body.map(chunk => try {
            chunk.asJson.prettyPrint.split("\\n").map("    " +).mkString("\n")
          } catch {
            case pe:org.parboiled.errors.ParsingException => 
              chunk.split("\\n").map("    " +).mkString("\n")
          }).mkString("\n")
        out.write(entityStr)
        out.newLine()
      })
  }
  
  
  override def logRequest(req:HttpRequest) = withWriter("-request-headers.inc")(out => {
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
    logMessage(out, req)
  })
  
  override def logResponse(resp:HttpResponse) = withWriter("-response-headers.inc")(out => {
    out.write(".. code-block:: http")
    out.newLine(); out.newLine()
    out.write("    " + resp.status.value)
    out.newLine()
    logMessage(out, resp)
  })
}

object NopLogger extends DocLogger {
  override def logRequest(req:HttpRequest) {}
  override def logResponse(resp:HttpResponse) {}
  override def logBody(reqOrResp:String, body:Seq[String]) {}
}