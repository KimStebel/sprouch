package sprouch

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class EtagSuite extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  import actorSystem.dispatcher
  
  test("get request with identical etag") {
    withNewDb(db => {
      val data = Test(0, "")
      for {
        doc <- db.createDoc(data)
        getAgain <- db.getDoc(doc)
      } yield {
        assert(doc === getAgain)
        assert(doc eq getAgain)
      }
    })
  }
  
}
