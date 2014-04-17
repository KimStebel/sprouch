package sprouch.docLogger

import spray.http.HttpRequest
import spray.http.HttpHeader

class JsGenerator extends CodeGenerator {
  override def generateCode(request:HttpRequest):Seq[String] = {
    def renderFields(indent:Int, fields:Seq[(String, Any)]):String = {
      fields.map {
        case (k, v:String) => indented(indent, singleQuoted(k) + ": " + jsSingleQuoted(v))
        case (k, v:Int) => indented(indent, singleQuoted(k) + ": " + v)
        case (k, v:Seq[(String, Any)]) => indented(indent, singleQuoted(k) + ": {\n") + renderFields(indent + 1, v) + "\n" + indented(indent, "}")
      }.mkString(",\n")
    }
    val method = request.method.name.toUpperCase
    val uri = "https://username.cloudant.com" + request.uri.toString
    val body = singleQuoted(request.entity.asString)
    val headers = request.headers.map(h => "'" + h.name + "': '" + h.value + "'").mkString(", ")
    val obj = renderFields(1, Seq(
        "url" -> uri,
        "type" -> method
    ) ++ (if (request.headers.nonEmpty) Seq(
        "headers" -> request.headers.toSeq.map { case HttpHeader(k,v) => k -> v }
    ) else Seq() ) ++ (if (!request.entity.isEmpty) Seq("data" -> request.entity.asString) else Seq()))
    Seq("$.ajax({") ++ obj.split("\\n").toSeq ++ Seq("});")
  }
}
