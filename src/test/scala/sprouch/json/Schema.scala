package sprouch.json

import org.scalatest.FunSuite
import spray.json._

class SchemaSuite extends FunSuite {
  test("random doc generation from schema") {
    val schema =
"""
{
  "player": {"type": "name"},
  "score": {"type": "int", "min":0, "max": 1000000},
  "game": {"type": "choice", "values": ["jetpack george", "mega plumber", "avenue warrior", "space conflict"]},
  "a": {"type": "nest", "value": {
    "b": {
      "type": "nest", "value": {
        "string": {"type": "string"},
        "integer": {"max": 1000000, "type": "int", "min": 0}
      }
    }
  }}
}"""
    val gameOptions = Seq("jetpack george", "mega plumber", "avenue warrior", "space conflict")
    val doc = Schema.complexSchemaFormat.read(schema.asJson).generate
    assert(
        doc.fields("player").isInstanceOf[JsString],
        "player field has type string")
    assert(
        doc.fields("score").isInstanceOf[JsNumber],
        "score field has type number")
    assert(
        gameOptions.contains(doc.fields("game").asInstanceOf[JsString].value),
        "game field contains value from options list")
    assert(
        doc.fields("a").asJsObject.fields("b").asJsObject.fields("string").isInstanceOf[JsString],
        "a -> b -> string of type string")
        
  }
    
}
