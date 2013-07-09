package sprouch

import spray.http.HttpRequest
import spray.http.HttpResponse
import java.io._
import spray.http.HttpMessage
import spray.json._

trait DocLogger {
  def logRequest(request:HttpRequest):Unit
  def logResponse(response:HttpResponse):Unit
}

object SphinxDocLogger {
  def apply(fileName:String) = {
    new SphinxDocLogger(System.getenv("TESTY_RESULT_DIR") + "/" + fileName)
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
  private def writeMessage(out:BufferedWriter, m:HttpMessage) {
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
      withWriter("-" + reqResp + "-body.inc")(out => {
        out.write(".. code-block:: javascript")
        out.newLine(); out.newLine()
        val entityStr = try {
          m.entity.asString.asJson.prettyPrint.split("\\n").map("    " +).mkString("\n") 
        } catch {
          case pe:org.parboiled.errors.ParsingException => 
            m.entity.asString.split("\\n").map("    " +).mkString("\n")
        }
        out.write(entityStr)
        out.newLine()
      })
    }
    
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
    out.write("    " + req.method + " " + prettyUri(req.uri) + " " + req.protocol)
    out.newLine()
    writeMessage(out, req)
  })
  override def logResponse(resp:HttpResponse) = withWriter("-response-headers.inc")(out => {
    out.write(".. code-block:: http")
    out.newLine(); out.newLine()
    out.write("    " + resp.status.value + " " + resp.status.reason)
    out.newLine()
    writeMessage(out, resp)
  })
}

object NopLogger extends DocLogger {
  override def logRequest(req:HttpRequest) {}
  override def logResponse(resp:HttpResponse) {}  
}