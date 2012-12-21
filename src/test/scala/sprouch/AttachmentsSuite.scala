package sprouch

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AttachmentsSuite extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  import actorSystem.dispatcher
  
  test("create, get, update, and delete attachment") {
    withNewDb(db => {
      val data = Test(0, "")
      val a = new Attachment("123", Array[Byte](-1,0,1,2,3))
      val attachmentWithSlash = new Attachment("ab/12////", Array[Byte](1,2,3))
      val a2 = a.copy(data=Array[Byte](1,2,3))
      for {
        doc <- db.createDoc(data)
        createRes <- db.putAttachment(doc, a)
        createRes2 <- db.putAttachment(createRes, attachmentWithSlash)
        updateRes <- db.putAttachment(createRes2, a2)
        getRes <- db.getAttachment(doc, a.id)
        doc2 <- db.getDoc[Test](doc.id)
        deleteRes <- db.deleteAttachment(updateRes, a2)
      } yield {
        assert(getRes === a2)
        assert(doc2.attachments.head._1 === a2.id)
      }
    })
  }
  
}