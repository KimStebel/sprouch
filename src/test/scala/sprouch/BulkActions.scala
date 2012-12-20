package sprouch

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BulkActions extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  import actorSystem.dispatcher
  
  test("create, get, update, and delete documents") {
    withNewDb(db => {
      val data = Seq(Test(0, "a"), Test(1, "b"), Test(2, "c")).map(new NewDocument(_))
      for {
        bulkInserted <- db.bulkPut(data)
        newData = bulkInserted.map(doc => doc.updateData(data => data.copy(foo=data.foo+1)))
        bulkUpdated <- db.bulkPut(newData)
        bulkGotten <- db.allDocs[Test](keys=data.map(_.id))
      } yield {
        bulkGotten
      }
    })
  }


}
