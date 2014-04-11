package sprouch.docLogger

import org.scalatest.FunSuite
import spray.http.HttpHeaders.{`Content-Type`, Cookie}
import spray.http.{HttpRequest, HttpMethods, HttpEntity, MediaType, ContentType, HttpCookie}



class CurlGeneratorSuite extends FunSuite {
  private val g = new CurlGenerator {}
  val ct = ContentType(MediaType.custom("application/json"))
  
  test("curl code generator can do simple get request") {
    val request = HttpRequest(
        method = HttpMethods.GET,
        uri = "https://kimstebel.cloudant.com/db/doc"
    )
    val code = g.generateCode(request)
    assert(code.toSeq === Seq("curl \"https://$user:$password@kimstebel.cloudant.com/db/doc\""))
  }
  
  test("curl code generator can do put request with json doc") {
    val request = HttpRequest(
        method = HttpMethods.PUT,
        uri = "https://kimstebel.cloudant.com/db/doc",
        headers = List(`Content-Type`(ct)),
        entity = HttpEntity(ct, """{"test": "\\\\"}""")
    )
    val code = g.generateCode(request)
    assert(code.toSeq === Seq("""curl "https://$user:$password@kimstebel.cloudant.com/db/doc" -H 'Content-Type: application/json' -d '{"test": "\\\\\\\\"}'"""))
  }
  
  test("curl code generator can make requests with multiple headers") {
    val request = HttpRequest(
        method = HttpMethods.PUT,
        uri = "https://kimstebel.cloudant.com/db/doc",
        headers = List(`Content-Type`(ct), Cookie(HttpCookie("eat", "me"))),
        entity = HttpEntity(ct, "{}")
    )
    val code = g.generateCode(request)
    assert(code.toSeq === Seq("""curl "https://$user:$password@kimstebel.cloudant.com/db/doc" -H 'Content-Type: application/json' -H 'Cookie: eat=me' -d '{}'"""))
  }
}