package sprouch

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import scala.concurrent.Future
import spray.json.{RootJsonFormat, JsonFormat, JsValue}
import spray.json.JsObject
import spray.json.JsString

class JsonProtocolSuite extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  case class FooIdRev(foo:String, _id:String, _rev:String)
  
  
  test("document format should handle data objects with _id and _rev properties") {
    implicit val dispatcher = (actorSystem.dispatcher)
    implicit val fooIdRevFormat = jsonFormat3(FooIdRev)
    val data = FooIdRev("hello", "world", "123")
    val doc = new RevedDocument("world", "123", data, Map())
    val df = implicitly[RootJsonFormat[RevedDocument[FooIdRev]]]
    assert(doc === df.read(df.write(doc)), "read composed with write should be identity")
    assert(df.write(doc) === JsObject(
        "foo" -> JsString("hello"),
        "_id" -> JsString("world"),
        "_rev" -> JsString("123"),
        "_attachments" -> JsObject()
    ))
  }
  
}
