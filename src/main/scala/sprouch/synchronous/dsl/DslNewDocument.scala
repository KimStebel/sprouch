package sprouch.synchronous.dsl

import sprouch.synchronous._
import java.util.UUID
import spray.json.RootJsonFormat
import sprouch.NewDocument
import sprouch.RevedDocument

trait DslDocument[A] {
  
}

class DslNewDocument[A:RootJsonFormat](id:String, data:A) extends NewDocument[A](id, data) with DslDocument[A] {
  def this(data:A) = this(UUID.randomUUID.toString.toLowerCase, data)
  
  def create(implicit db:Database):RevedDocument[A] = db.createDoc(data)  
  def create(id:String)(implicit db:Database):RevedDocument[A] = db.createDoc(id, data)

}
