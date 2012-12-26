package sprouch.dsl

import sprouch._
import java.util.UUID
import spray.json.RootJsonFormat
import scala.concurrent.Future

trait DslDocument[A] {
  
}

class DslNewDocument[A:RootJsonFormat](id:String, data:A) extends NewDocument[A](id, data) with DslDocument[A] {
  def this(data:A) = this(UUID.randomUUID.toString.toLowerCase, data)
  
  def create(implicit db:Database):Future[RevedDocument[A]] = db.createDoc(data)  
  def create(id:String)(implicit db:Database):Future[RevedDocument[A]] = db.createDoc(id, data)

}