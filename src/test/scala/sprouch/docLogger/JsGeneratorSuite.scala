package sprouch.docLogger

import org.scalatest.FunSuite
import spray.http.HttpRequest

import spray.http.{HttpRequest, HttpMethods, HttpEntity, Uri}
import spray.http.{ContentType, MediaType}
import spray.http.HttpHeaders._


class JsGeneratorSuite extends FunSuite {
  val g = new JsGenerator
  val ct = ContentType(MediaType.custom("application/json"))

  test("js generator does simple get request, nicely formatted") {
    val uriStr = "http://kimstebel.cloudant.com/db/doc"
    val generatedCode = g.generateCode(HttpRequest(
        method = HttpMethods.GET,
        uri = uriStr
    ))
    val exp = Seq(
        "$.ajax({",
        "    'url': '" + "http://kimstebel.cloudant.com/db/doc" + "',",
        "    'type': 'GET'",
        "});"
    )
    assert (generatedCode.size === exp.size)
    for ((le, lg) <- exp.zip(generatedCode)) {
      assert(lg === le)
    }
  }
  
  test("js generator does put request with json body") {
    val uriStr = "http://kimstebel.cloudant.com/db/doc"
    val generatedCode = g.generateCode(HttpRequest(
        method = HttpMethods.GET,
        uri = uriStr,
        headers = List(`Content-Type`(ct)),
        entity = HttpEntity(ct, "{'test': '\\\\'}")
    ))
    println(generatedCode.mkString("\n"))
    
    val startCode = Seq(
      """(function(){""",
      """var $ = { ajax: function(obj){ request = obj } };"""    
    )
    val endCode = Seq(
      "if (request.url !== 'http://kimstebel.cloudant.com/db/doc') return 1;",
      "if (request.type !== 'GET') return 2;",
      "if (request.headers['content-type'] !== 'application/json') return 3;",
      """if (request.data !== "{'test': '\\\\'}") return request.data;""",
      "return 0;",
      "})();"
    )
    val code = startCode ++ generatedCode ++ endCode
    assert(executeJs(code) === 0)
  }
  
  private def executeJs(code:Seq[String]):Any = {
    import org.mozilla.javascript._
    val cx = Context.enter()
    try {
      val scope = cx.initStandardObjects
      val result = cx.evaluateString(scope, code.mkString("\n"), "sourcename", 1, null);
      result match {
        case r:java.lang.Double => r.toInt
        case r:java.lang.Integer => r.toInt
        case r:Any => r
      }
    } catch {
      case e:Throwable => {
        println(e)
        -1
      }
    } finally {
      Context.exit()
    }
  }

}