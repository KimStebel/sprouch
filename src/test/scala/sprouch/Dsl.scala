package sprouch

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import sprouch.dsl._

@RunWith(classOf[JUnitRunner])
class Dsl extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  import actorSystem.dispatcher
  
  test("create, read, update, and delete docs with dsl") {
    withNewDbFuture(implicit dbf => {
      val data = Test(0, "")
      val data2 = Test(1, "")
      for {
        doc0 <- data.create
        doc1 <- data.create(id = "someid")
        doc2 <- doc0 := data2
        doc2b <- get[Test](doc2.id)
        doc2c <- get(doc2)
        doc3 <- doc2.delete
      } yield {
        assert(doc0.data === data)
        assert(doc2.data === data2)
        assert(doc2 === doc2b)
        assert(doc2 === doc2c)
        assert(doc0 != doc1)
        assert(doc0.data === doc1.data)
      }
    })
  }
  test("attachments with dsl") {
    withNewDbFuture(implicit dbf => {
      val data = Test(0, "")
      val attachmentData = Array[Byte](1,2,3,4)
      for {
        doc0 <- data.create
        doc1 <- doc0.attach("aid" -> attachmentData)
        attachment <- doc1.attachment("aid")
        attachmentDeleted <- doc1.deleteAttachment("aid")
      } yield {
        assert(doc1.attachments.get("aid").isDefined, "attachment exists")
        assert(attachment.data === attachmentData)
        assert(attachmentDeleted.attachments.isEmpty, "no attachments")
      }
    })
  }
  
}