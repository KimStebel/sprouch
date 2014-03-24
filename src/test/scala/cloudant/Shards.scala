package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import sprouch.dsl._
import spray.json.JsonWriter
import spray.json.JsObject
import spray.json.JsonReader

class Shards extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  test("_shards") {
    implicit val dispatcher = actorSystem.dispatcher
        
    withNewDbFuture(implicit dbf => {
      val dl = SphinxDocLogger("shards")
      val dlid = SphinxDocLogger("shardForId")
      for {
        db <- dbf
        docId = "foo"
        doc <- db.createDocId(docId, Empty)
        res1 <- db.shards(docLogger = dl)
        res2 <- db.shardForDoc(docId, docLogger = dlid)
      } yield {
        assert(res1.shards.size > 0, "at least one shard")
        assert(res2.nodes.size > 0, "at least one node has the document")
      }
    })
  }
}
