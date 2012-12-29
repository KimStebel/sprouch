package sprouch.synchronous.dsl

import sprouch.synchronous._
import spray.json.RootJsonFormat
import sprouch.JsonProtocol.OkResponse
import sprouch.{AttachmentStub, RevedDocument, Attachment}

class DslRevedDocument[A](id:String, rev:String, data:A, attachments:Map[String, AttachmentStub]) 
  extends RevedDocument[A](id, rev, data, attachments) {

  def :=[B](data:B)(implicit db:Database, rjf:RootJsonFormat[B]):RevedDocument[B] = {
    db.updateDoc(this.updateData(_ => data))
  }
  def delete(implicit db:Database):OkResponse = db.deleteDoc(this)
  def attach(attachment:(String, Array[Byte]))
    (implicit db:Database, rjf:RootJsonFormat[A]):RevedDocument[A] =
    attachment match {
      case (id, array) => db.putAttachment(this, new Attachment(id, array))
    }
  
  def attachment(id:String)(implicit db:Database):Attachment = db.getAttachment(this, id)
  def deleteAttachment(id:String)
    (implicit db:Database):RevedDocument[A] = db.deleteAttachment(this, id)
}
