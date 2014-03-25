package cloudant

import org.scalatest.FunSuite
import sprouch._

class Shards extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  import actorSystem.dispatcher
  
  test("_shards") {
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
