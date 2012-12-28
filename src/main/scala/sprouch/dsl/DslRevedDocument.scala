package sprouch.dsl

import sprouch._
import akka.dispatch.Future
import spray.json.RootJsonFormat
import sprouch.JsonProtocol.OkResponse

class DslRevedDocument[A](id:String, rev:String, data:A, attachments:Map[String, AttachmentStub]) 
  extends RevedDocument[A](id, rev, data, attachments) {

  def :=[B](data:B)(implicit db:Future[Database], rjf:RootJsonFormat[B]):Future[RevedDocument[B]] = {
    db.flatMap(_.updateDoc(this.updateData(_ => data)))
  }
  def delete(implicit db:Future[Database]):Future[OkResponse] = db.flatMap(_.deleteDoc(this))
  def attach(attachment:(String, Array[Byte]))(implicit db:Future[Database], rjf:RootJsonFormat[A]):Future[RevedDocument[A]] =
    attachment match {
      case (id, array) => db.flatMap(_.putAttachment(this, new Attachment(id, array)))
    }
  
  def attachment(id:String)(implicit db:Future[Database]):Future[Attachment] = db.flatMap(_.getAttachment(this, id))
  def deleteAttachment(id:String)(implicit db:Future[Database]):Future[RevedDocument[A]] =
    db.flatMap(_.deleteAttachment(this, id))
}