package sprouch.dsl

import sprouch._
import java.util.UUID
import spray.json.RootJsonFormat
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait DslDocument[A] {
  
}

class DslNewDocument[A:RootJsonFormat](id:String, data:A) extends NewDocument[A](id, data) with DslDocument[A] {
  def this(data:A) = this(UUID.randomUUID.toString.toLowerCase, data)
  
  def create(implicit db:Future[Database], executionContext:ExecutionContext):Future[RevedDocument[A]] = db.flatMap(_.createDoc(data))  
  def create(id:String)(implicit db:Future[Database], executionContext:ExecutionContext):Future[RevedDocument[A]] = db.flatMap(_.createDoc(id, data))

}