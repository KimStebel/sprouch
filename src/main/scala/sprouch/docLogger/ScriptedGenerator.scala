package sprouch.docLogger

import spray.http.HttpRequest
import java.io._

class ScriptedGenerator(script:String) extends CodeGenerator {
  case class Header(name:String, value:String)
  case class Request(method:String, uri:String, headers:Array[Header], body:String)
  def request(hr:HttpRequest) = Request(hr.method.name.toUpperCase, hr.uri.toString, hr.headers.map(h => Header(h.name, h.value)).toArray, "")
  
  override def generateCode(request:HttpRequest):Seq[String] = {
    val sw = new StringWriter
    val writer = new BufferedWriter(sw)
    executeJs(request, writer, script)
    writer.flush
    sw.toString.split("\\n").toSeq
  }
  
  private def executeJs(request:HttpRequest, writer:BufferedWriter, code:String):Any = {
    import org.mozilla.javascript._
    val cx = Context.enter()
    try {
      val scope = cx.initStandardObjects
      val wrappedRequest = Context.javaToJS(request, scope);
      ScriptableObject.putProperty(scope, "request", wrappedRequest);
      val wrappedWriter = Context.javaToJS(writer, scope);
      ScriptableObject.putProperty(scope, "out", wrappedWriter);
      val result = cx.evaluateString(scope, code, "sourcename", 1, null)
      
      result
    } finally {
      Context.exit()
    }
  }
}
