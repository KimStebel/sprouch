package sprouch

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class OldRevisions extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  import actorSystem.dispatcher
  
  test("get old revisions of a document") {
    withNewDb(db => {
      val data = Test(0, "")
      for {
        doc1 <- db.createDoc(data)
        doc2 <- db.updateDoc(doc1.updateData(_.copy(foo=1)))
        doc3 <- db.updateDoc(doc2.updateData(_.copy(foo=1)))
        revisions <- db.revisions(doc3)
      } yield {
        assert(revisions.map(_.rev) === Seq(doc3,doc2,doc1).map(_.rev))
      }
    })
  }
}
