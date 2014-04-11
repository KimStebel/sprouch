package sprouch.docLogger

import org.scalatest.FunSuite
import spray.http.{HttpRequest, HttpMethods, HttpEntity}
import spray.http.HttpEntity.Empty
import scala.sys.process._
import java.io.ByteArrayInputStream
import spray.http.{ContentType, MediaType}
import spray.http.HttpHeaders._
import spray.http.Uri.apply

class PythonGeneratorSuite extends FunSuite {
private val r = new PythonGenerator {}
  private def initCode(tests:Seq[String]) = Seq(
      "import requests",
      "user = 'kimstebel'",
      "password = 'thispasswordisstrong'",
      "from httmock import all_requests, response, HTTMock",
      "import requests",
      "",
      "@all_requests",
      "def response_content(url, request):",
      "    headers = {'content-type': 'application/json'}",
      "    content = {}") ++ tests ++ Seq(
      "    return response(200, content, headers, None, 5, request)",
      "",
      "with HTTMock(response_content):"
    )
  private val endCode  = Seq(
    "exit(0)"
  )
  
  test("python renderer renders simple get request") {
    val tests = Seq(
      "    if request.headers['Authorization'] != u'Basic a2ltc3RlYmVsOnRoaXNwYXNzd29yZGlzc3Ryb25n':",
      "        exit(13)",
      "    if request.body != None:",
      "        exit(12)",
      "    if request.method != 'GET':",
      "        exit(10)",
      "    if request.url != 'http://kimstebel.cloudant.com/_all_dbs':",
      "        exit(11)"
    )
    val generatedCode = r.generateCode(HttpRequest(
        method = HttpMethods.GET,
        uri = "http://kimstebel.cloudant.com/_all_dbs",
        headers = Nil,
        entity = Empty)
    )
    val code = initCode(tests) ++ generatedCode.map("    " +) ++ endCode
    assert(execPython(code) === 0)
  }
  
  test("python renderer renders post request with json body and content-type header") {
    val tests = Seq(
      "    if request.headers['Authorization'] != u'Basic a2ltc3RlYmVsOnRoaXNwYXNzd29yZGlzc3Ryb25n':",
      "        exit(13)",
      "    if request.body != \"{'test': '\\\\\\\\'}\":",//yes, 8!
      "        exit(12)",
      "    if request.method != 'POST':",
      "        exit(10)",
      "    if request.url != 'http://kimstebel.cloudant.com/db/doc':",
      "        exit(11)",
      "    if request.headers['Content-Type'] != u'application/json':",
      "        exit(13)"
    )
    val ct = ContentType(MediaType.custom("application/json"))
    val generatedCode = r.generateCode(HttpRequest(
        method = HttpMethods.POST,
        uri = "http://kimstebel.cloudant.com/db/doc",
        headers = List(`Content-Type`(ct)),
        entity = HttpEntity.apply(ct, "{'test': '\\\\'}"))
    )
    val code = initCode(tests) ++ generatedCode.map("    " +) ++ endCode
    assert(execPython(code) === 0)
  }  
  
  private def execPython(code:Seq[String]) = {
    val inputString = code.mkString("\n")
    val is = new ByteArrayInputStream(inputString.getBytes("UTF-8"))
    val pb = Process(Seq("python")) #< is
    val p = pb.run()
    p.exitValue
  }
}