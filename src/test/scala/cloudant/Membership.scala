package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import sprouch.dsl._
import spray.json.JsonWriter
import spray.json.JsObject
import spray.json.JsonReader

class Membership extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  test("_membership") {
    implicit val dispatcher = actorSystem.dispatcher
        
    val dl = SphinxDocLogger("membership")
    for {
      res <- c.membership(docLogger = dl)
    } yield {
      assert(res.cluster_nodes.toSet.subsetOf(res.all_nodes.toSet), "all_nodes must be a subset of cluster_nodes")
    }
  }
}
