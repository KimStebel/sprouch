package sprouch.docLogger

import org.scalatest.FunSuite
import spray.http.HttpHeaders.{ `Content-Type`, Cookie }
import spray.http.{ HttpRequest, HttpMethods, HttpEntity, MediaType, ContentType, HttpCookie }
import java.io.{ File, FileWriter }
import javax.tools.ToolProvider
import java.net.URLClassLoader
import org.apache.commons.codec.binary.Base64

class JavaGeneratorSuite extends FunSuite {
  private val g = new JavaGenerator
  val ct = ContentType(MediaType.custom("application/json"))

  test("java code generator can do simple get request") {
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = "https://user.cloudant.com/db/doc")
    val code = g.generateCode(request)
    val testCode = Seq(
      """        if (httpGet.headers.get("Authorization") == null) throw new RuntimeException("Authorization header not set.");""",
      """        if (!DefaultHttpClient.executeCalled) throw new RuntimeException("execute not called");""",
      """        if (!"https://user.cloudant.com/db/doc".equals(httpGet.url)) throw new RuntimeException("url incorrect: " + httpGet.url);""")

    runCode(code, testCode)
  }
  /*
  test("java code generator can do put request with json doc") {
    val request = HttpRequest(
      method = HttpMethods.PUT,
      uri = "https://kimstebel.cloudant.com/db/doc",
      headers = List(`Content-Type`(ct)),
      entity = HttpEntity(ct, """{"test": "\\\\"}"""))
    val code = g.generateCode(request)
    val testCode = Seq(
      
      ""
    )
    runCode(code, testCode)
  }// */
  /*
  test("java code generator can make requests with multiple headers") {
    val request = HttpRequest(
        method = HttpMethods.PUT,
        uri = "https://kimstebel.cloudant.com/db/doc",
        headers = List(`Content-Type`(ct), Cookie(HttpCookie("eat", "me"))),
        entity = HttpEntity(ct, "{}")
    )
    val code = g.generateCode(request)
    fail("todo")
  }// */

  private def runCode(codeUnderTest: Seq[String], testCode: Seq[String]) = {

    def template(generated: Seq[String], testing: Seq[String]) = {
      Seq(
        """package test;""",
        "",
        """import java.util.Map;""",
        """import java.util.HashMap;""",
        "",
        """class Base64 { public static String encodeBase64(byte[] bytes) {return "";} }""",
        """class DefaultHttpClient { static boolean executeCalled = false; public HttpResponse execute(HttpGet getReq) { executeCalled = true; return null;}; }""",
        """class HttpResponse {}""",
        """class HttpGet { public Map<String, String> headers = new HashMap<String,String>(); public void setHeader(String key, String value) { headers.put(key, value); }; public String url; public HttpGet(String url){ this.url = url; }}""",
        "",
        """public class Test implements Runnable {""",
        """    @Override public void run() {""",
        """        String user = "user";""",
        """        String pass = "pass";""") ++
        generated ++ testing ++
        Seq(
          """        """,
          """    }""",
          """}""")
    }
    val source = template(codeUnderTest, testCode).mkString("\n")
    val cp = System.getenv("TESTY_RESULT_DIR")
    val sourceFile = new File(cp + "/test/Test.java")
    val classFile = new File(cp + "/test/Test.class")
    val parentDir = sourceFile.getParentFile
    parentDir.mkdirs()
    new FileWriter(sourceFile).append(source).close()
    val compiler = ToolProvider.getSystemJavaCompiler
    compiler.run(null, null, null, sourceFile.getPath)
    val classLoader = URLClassLoader.newInstance(Array(new File(cp).toURI.toURL))
    val cls = Class.forName("test.Test", true, classLoader)
    val instance = cls.newInstance.asInstanceOf[Runnable]
    instance.run()
    Seq(sourceFile, classFile, parentDir).foreach(_.delete())
  }
}