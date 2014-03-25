package cloudant

import org.scalatest.FunSuite
import sprouch._

class Membership extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  import actorSystem.dispatcher

  test("_membership") {
        
    val dl = SphinxDocLogger("membership")
    for {
      res <- c.membership(docLogger = dl)
    } yield {
      assert(res.cluster_nodes.toSet.subsetOf(res.all_nodes.toSet), "all_nodes must be a subset of cluster_nodes")
    }
  }
}
