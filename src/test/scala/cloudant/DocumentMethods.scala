package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._

class DocumentMethods extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  implicit val dispatcher = actorSystem.dispatcher
  
  test("get doc") {
    await(for {
      _ <- ignoreFailure(c.deleteDb("db"))
      db <- c.createDb("db")
      doc <- db.createDoc("DocID", randomPerson())
      doc2 <- c.withLog("getDoc") {
        db.getDoc[Person]("DocID")
      }
      doc2wa <- c.withLog("putAtt") {
        db.putAttachment(doc, Attachment("my attachment", Array(0,1,2,3)))
      }
      doc3 <- c.withLog("getDocAtt") {
        db.getDoc[Person]("DocID")
      }
      doc4 <- c.withLog("delAtt") {
        db.deleteAttachment(doc3, "my attachment")
      }
      revs <- c.withLog("getRevs") {
        db.revisions(doc)
      }
    } yield {
      assert(doc === doc2)
    })
    
  }
    
  test("put doc") {
    await(for {
      _ <- ignoreFailure(c.deleteDb("db"))
      db <- c.createDb("db")
      doc <- c.withLog("putDoc") {
        db.createDoc("DocID", randomPerson())
      }
      doc2 <- c.withLog("putDoc2") {
        db.updateDoc(doc.updateData(_.copy(age=40)))
      }
    } yield {
      assert(doc.id === doc2.id)
    })
  }
}
