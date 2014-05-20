package sprouch.docLogger

import org.scalatest.FunSuite
import spray.http.HttpRequest

import spray.http.{HttpRequest, HttpMethods, HttpEntity, Uri}
import spray.http.{ContentType, MediaType}
import spray.http.HttpHeaders._


class ScriptedGeneratorSuite extends FunSuite {
  val ct = ContentType(MediaType.custom("application/json"))

  test("scripted generator supples request info to script") {
    val g = new ScriptedGenerator("""
      out.writeLn("haha");
      out.write(request.method().name().toUpperCase());
    """)
    val uriStr = "http://kimstebel.cloudant.com/db/doc"
    val generatedCode = g.generateCode(HttpRequest(
        method = HttpMethods.PUT,
        uri = uriStr,
        headers = List(`Content-Type`(ct)),
        entity = HttpEntity(ct, """{ "test": 5 }""")
    ))
    val expected = """
      
      """.trim.split("\\n").map(_.trim)
    assert (generatedCode === expected)
  }
  
}