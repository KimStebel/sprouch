package sprouch

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import sprouch.dsl._
import scala.concurrent.Future

class DslBulk extends FunSuite with CouchSuiteHelpers {
  
  import JsonProtocol._
  import actorSystem.dispatcher
  
  test("create docs in bulk with dsl") {
    withNewDbFuture(implicit dbf => {
      val data = for (i <- 1 to 10) yield Test(i, "")
      for {
        docs0 <- data.create
        db <- dbf
        allDocs <- db.allDocs[Test]()
        docs1 <- {
          val newDocs = docs0.map(doc => doc.updateData(d => d.copy(foo = d.foo + 1)))
          newDocs.update  
        }
        docs2 <- docs1.get
      } yield {
        assert(docs0.map(_.data) === data)
        val docs = allDocs.rows.seq.map(_.doc)
        assert(docs0.map(_.data).toSet === docs.toSet)
        assert(docs1.map(_.data) === data.map(d => d.copy(foo = d.foo + 1)))
        assert(docs2.rows.seq.map(_.doc).toSet === docs1.map(_.data).toSet)
      }
    })
  } 
}
