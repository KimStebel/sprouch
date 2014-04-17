package sprouch.docLogger

import spray.http.HttpRequest
import spray.http.HttpEntity.Empty

class CurlGenerator extends CodeGenerator {
  private def doubleQuoted(s:String) = "\"" + escapeDoublequote(escapeBackslash(s)) + "\""
  
  override def generateCode(request:HttpRequest):Seq[String] = {
    val method = request.method.name.toLowerCase
    val uri = doubleQuoted("https://${user}:${password}@${user}.cloudant.com" + request.uri.toString)
    val body = if (request.entity == Empty) "" else " -d " + singleQuoted(request.entity.asString)
    val headers = request.headers.map(h => " -H " + singleQuoted(h.name + ": " + h.value)).mkString("")
    Seq("curl " + uri + headers + body)
  }
}
