package sprouch.docLogger

import spray.http.HttpRequest

class JavaGenerator extends CodeGenerator {
  private def doubleQuoted(s:String):String = "\"" + replaceLinebreak(escapeBackslash(s)).replaceAll("\"","\\\\\"") + "\""
  
  override def generateCode(request:HttpRequest):Seq[String] = {
    val url = request.uri.toString
    val methodUpperCamel = {
      val m = request.method.toString
      m.head.toUpper + m.tail.toLowerCase
    }
    Seq(
      """DefaultHttpClient httpClient = new DefaultHttpClient();""",
      """Http""" + methodUpperCamel + """ request = new Http""" + methodUpperCamel + "(" + doubleQuoted("https://") + " + user + " + doubleQuoted(".cloudant.com" + url) + ");",
      """String encodedCreds = new String(Base64.encodeBase64((user + ":" + pass).getBytes()));""",
      """request.setHeader("Authorization", "Basic " + encodedCreds);""") ++
    ( if (request.entity.isEmpty) Seq() else Seq(
      """request.setEntity(new StringEntity(""" + doubleQuoted(request.entity.asString) + """, ContentType.APPLICATION_JSON));"""
    )) ++
    Seq(
      """HttpResponse response = httpClient.execute(request);"""
    )
  }
}

