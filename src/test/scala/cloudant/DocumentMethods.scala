package cloudant

import org.scalatest.FunSuite
import sprouch._

class DocumentMethods extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  import actorSystem.dispatcher
  
  test("get doc") {
    val dbName = randomDbName
    await(for {
      _ <- ignoreFailure(c.deleteDb(dbName))
      db <- c.createDb(dbName)
      _ <- pause()
      doc <- db.createDocId("DocID", randomPerson())
      doc2 <- db.getDoc[Person](
          "DocID",
          docLogger = SphinxDocLogger("getDoc"))
      doc2wa <- db.putAttachment(
          doc,
          Attachment("my attachment", Array(0,1,2,3)),
          docLogger = SphinxDocLogger("putAtt"))
      doc3 <- db.getDoc[Person]("DocID", SphinxDocLogger("getDocAtt"))
      doc4 <- db.deleteAttachmentId(
          doc3,
          "my attachment",
          docLogger = SphinxDocLogger("delAtt"))
      revs <- db.revisions(doc, docLogger = SphinxDocLogger("getRevs"))
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
          docLogger = SphinxDocLogger("putDoc"))
      doc2 <- db.updateDoc(
          doc.updateData(_.copy(age=40)),
          docLogger = SphinxDocLogger("putDoc2"))
    } yield {
      assert(doc.id === doc2.id)
      //TODO more checks
    })
  }
}
