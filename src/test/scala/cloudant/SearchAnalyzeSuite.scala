package cloudant

import org.scalatest.FunSuite

import sprouch.CouchSuiteHelpers
import sprouch.docLogger.MdDocLogger

class SearchAnalyzeSuite extends FunSuite with CouchSuiteHelpers {
  import sprouch.JsonProtocol._
  
  test("_search_analyze json/post version") {
    implicit val dispatcher = actorSystem.dispatcher
    val dl = MdDocLogger("_search_analyze")
    await(for {
      result <- c.searchAnalyze("standard", "This is a test for the standard analyzer", dl)
    } yield {
      val expected = Seq("test", "standard", "analyzer")
      assert(result.tokens === expected)
    })
    
  }
}
