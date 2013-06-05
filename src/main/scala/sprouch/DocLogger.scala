package sprouch

import spray.http.HttpRequest
import spray.http.HttpResponse
import java.io._
import spray.http.HttpMessage

trait DocLogger {
  def logRequest(request:HttpRequest):Unit
  def logResponse(response:HttpResponse):Unit
}

case class SphinxDocLogger(fileName:String) extends DocLogger {
  private def withWriter(fileName:String, append:Boolean)(f:BufferedWriter=>Unit) = {
    val out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(fileName, append)))
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
    import spray.json._
    if (!m.entity.isEmpty) {
      val reqResp = m match {
        case _:HttpRequest => "request"
        case _:HttpResponse => "response"
      }
      withWriter(fileName + "-" + reqResp + "-body.inc", append = false)(out => {
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
  override def logRequest(req:HttpRequest) = withWriter(fileName + "-request-headers.inc", append=false)(out => {
    out.write(".. code-block:: http")
    out.newLine(); out.newLine()
    out.write("    " + req.method + " " + req.uri + " " + req.protocol)
    out.newLine()
    writeMessage(out, req)
  })
  override def logResponse(resp:HttpResponse) = withWriter(fileName + "-response-headers.inc", append=false)(out => {
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