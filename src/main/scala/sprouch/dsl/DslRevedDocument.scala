package sprouch.dsl

import sprouch._
import akka.dispatch.Future
import spray.json.RootJsonFormat
import sprouch.JsonProtocol.OkResponse

class DslRevedDocument[A](id:String, rev:String, data:A, attachments:Map[String, AttachmentStub]) 
  extends RevedDocument[A](id, rev, data, attachments) {

  def :=[B](data:B)(implicit db:Database, rjf:RootJsonFormat[B]):Future[RevedDocument[B]] = {
    db.updateDoc(this.updateData(_ => data))
  }
  def delete(implicit db:Database):Future[OkResponse] = db.deleteDoc(this)
  def attach(attachment:(String, Array[Byte]))(implicit db:Database, rjf:RootJsonFormat[A]):Future[RevedDocument[A]] =
    attachment match {
      case (id, array) => db.putAttachment(this, new Attachment(id, array))
    }
  
  def attachment(id:String)(implicit db:Database):Future[Attachment] = db.getAttachment(this, id)
  def deleteAttachment(id:String)(implicit db:Database):Future[RevedDocument[A]] = db.deleteAttachment(this, id)
}