package sprouch.synchronous

import spray.json.RootJsonFormat
import sprouch.RevedDocument

package object dsl {
  implicit def dataToDslDoc[A:RootJsonFormat](data:A):DslNewDocument[A] = {
    new DslNewDocument(data)
  }
  implicit def dslDoc[A:RootJsonFormat](doc:RevedDocument[A]):DslRevedDocument[A] = {
    new DslRevedDocument(doc.id, doc.rev, doc.data, doc.attachments)
  }
  def get[A](id:String)(implicit db:Database, rjf:RootJsonFormat[A]):RevedDocument[A] = {
    db.getDoc[A](id)
  }
  def get[A](doc:RevedDocument[A])(implicit db:Database, rjf:RootJsonFormat[A]):RevedDocument[A] = {
    db.getDoc[A](doc)
  }
}
