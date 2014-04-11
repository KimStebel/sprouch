package sprouch.docLogger

import spray.http.HttpRequest
import spray.http.HttpResponse
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.io.FileOutputStream

class PythonGenerator extends CodeGenerator {
  
  override def generateCode(request:HttpRequest):Seq[String] = {
    val method = request.method.name.toLowerCase
    val uri = singleQuoted("https://username.cloudant.com" + request.uri.toString)
    val body = singleQuoted(request.entity.asString)
    val headers = request.headers.map(h => "'" + h.name + "': '" + h.value + "'").mkString(", ")
    Seq(
      indented(0, "response = requests." + method + "("),
      indented(1, uri + ",")) ++
      (if (request.entity.nonEmpty) Seq(indented(1, "data = " + body + ",")) else Seq()) ++ Seq(
      indented(1, "headers = {" + headers + "},"),
      indented(1, "auth = (user,password)"),
      indented(0, ")")
    )
  }
  
}
