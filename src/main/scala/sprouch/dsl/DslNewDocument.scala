package sprouch.dsl

import sprouch._
import java.util.UUID
import spray.json.RootJsonFormat
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait DslDocument[A] {
  
}

class DslNewDocument[A:RootJsonFormat](id:String, data:A, attachments:Map[String, AttachmentStub])
    extends NewDocument[A](id, data, attachments) with DslDocument[A] {
  def this(data:A) = this(UUID.randomUUID.toString.toLowerCase, data, Map())
  
  def create(implicit db:Future[Database], executionContext:ExecutionContext):Future[RevedDocument[A]] = db.flatMap(_.createDoc(data))  
  def create(id:String)(implicit db:Future[Database], executionContext:ExecutionContext):Future[RevedDocument[A]] = db.flatMap(_.createDoc(id, data))
  def createViews(implicit db:Future[Database], ev:A =:= Views, ec:ExecutionContext):Future[RevedDocument[Views]] = {
    db.flatMap(_.createViews(this.asInstanceOf[NewDocument[Views]])) //cast will always work due to evidence parameter
  }
}

class DslNewDocSeq[A:RootJsonFormat](data:Seq[A]) {
  def create(implicit db:Future[Database], ec:ExecutionContext):Future[Seq[RevedDocument[A]]] = {
    db.flatMap(_.bulkPut(data.map(new NewDocument(_))))
  }
}
