package sprouch.dsl

import sprouch._
import scala.concurrent.Future
import spray.json.RootJsonFormat
import sprouch.JsonProtocol.OkResponse
import scala.concurrent.ExecutionContext
import sprouch.JsonProtocol.AllDocsResponse
import spray.json.JsonFormat

class DslRevedDocument[A](id:String, rev:String, data:A, attachments:Map[String, AttachmentStub]) 
  extends RevedDocument[A](id, rev, data, attachments) {

  def :=[B](data:B)(implicit db:Future[Database], rjf:RootJsonFormat[B], executionContext:ExecutionContext):Future[RevedDocument[B]] = {
    db.flatMap(_.updateDoc(this.updateData(_ => data)))
  }
  def delete(implicit db:Future[Database], executionContext:ExecutionContext):Future[OkResponse] = db.flatMap(_.deleteDoc(this))
  def attach(attachment:(String, Array[Byte]))(implicit db:Future[Database], rjf:RootJsonFormat[A], executionContext:ExecutionContext):Future[RevedDocument[A]] =
    attachment match {
      case (id, array) => db.flatMap(_.putAttachment(this, new Attachment(id, array)))
    }
  
  def attachment(id:String)(implicit db:Future[Database], executionContext:ExecutionContext):Future[Attachment] = db.flatMap(_.getAttachment(this, id))
  def deleteAttachment(id:String)(implicit db:Future[Database], executionContext:ExecutionContext):Future[RevedDocument[A]] =
    db.flatMap(_.deleteAttachment(this, id))
  def get(implicit db:Future[Database], rjf:RootJsonFormat[A], ec:ExecutionContext):Future[RevedDocument[A]] = db.flatMap(_.getDoc[A](this))
}

class DslRevedDocSeq[A:RootJsonFormat](data:Seq[RevedDocument[A]]) {
  def update(implicit db:Future[Database], ec:ExecutionContext):Future[Seq[RevedDocument[A]]] = {
    db.flatMap(_.bulkPut(data))
  }
  def get(implicit db:Future[Database], ec:ExecutionContext):Future[AllDocsResponse[A]] = {
    db.flatMap(_.allDocs[A](keys = data.map(_.id)))
  }
}
