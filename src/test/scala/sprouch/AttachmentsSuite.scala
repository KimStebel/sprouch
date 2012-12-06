package sprouch

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class AttachmentsSuite extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  test("create, get, update, and delete attachment") {
    withNewDb(db => {
      val data = Test(0, "")
      val a = new Attachment("123", Array[Byte](-1,0,1,2,3))
      val a2 = a.copy(data=Array[Byte](1,2,3))
      for {
        doc <- db.createDoc(data).map(_.right.get)
        createRes <- db.putAttachment(doc, a)
        updateRes <- db.putAttachment(createRes.right.get, a2)
        getRes <- db.getAttachment(doc, a.id)
        doc2 <- db.getDoc[Test](doc.id).map(_.right.get)
        deleteRes <- db.deleteAttachment(updateRes.right.get, a2)
      } yield {
        List(createRes, updateRes, getRes, deleteRes).foreach(res => assert(res.isRight, res))
        assert(getRes.right.get === a2)
        assert(doc2.attachments.head._1 === a2.id)
      }
    })
  }
  
}