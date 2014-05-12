package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import docLogger._

class DocumentMethodsSuite extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  implicit val dispatcher = actorSystem.dispatcher
  
  test("get doc") {
    val dbName = randomDbName
    await(for {
      _ <- ignoreFailure(c.deleteDb(dbName))
      db <- c.createDb(dbName)
      _ <- pause()
      doc <- db.createDocId("DocID", randomPerson())
      doc2 <- db.getDoc[Person](
          "DocID",
          docLogger = MdDocLogger("Document_GET"))
      doc2wa <- db.putAttachment(
          doc,
          Attachment("my attachment", Array(0,1,2,3)),
          docLogger = MdDocLogger("Attachment_PUT"))
      doc3 <- db.getDoc[Person]("DocID", MdDocLogger("Attachment_GET"))
      doc4 <- db.deleteAttachmentId(
          doc3,
          "my attachment",
          docLogger = MdDocLogger("Attachment_DELETE"))
      revs <- db.revisions(doc, docLogger = MdDocLogger("Document_GET-Revs"))
    } yield {
      assert(doc === doc2)
      //TODO more checks
    })
  }
  
  test("put doc") {
    await(for {
      _ <- ignoreFailure(c.deleteDb("db"))
      db <- c.createDb("db")
      doc <- db.createDocId(
          "DocID",
          randomPerson(),
          docLogger = MdDocLogger("Document_PUT"))
      doc2 <- db.updateDoc(
          doc.updateData(_.copy(age=40)),
          docLogger = MdDocLogger("putDoc2"))
      doc3 <- db.deleteDoc(doc2, docLogger = MdDocLogger("Document_DELETE"))
    } yield {
      assert(doc.id === doc2.id)
      //TODO more checks
    })
  }
}
