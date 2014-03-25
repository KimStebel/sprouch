package sprouch
package dsl

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import spray.json.RootJsonFormat

trait DslDocument[A] {

}

class DslNewDocument[A:RootJsonFormat](id: String, data: A, attachments: Map[String, AttachmentStub])
    extends NewDocument[A](id, data, attachments) with DslDocument[A] {
  def this(data:A) = this(UUID.randomUUID.toString.toLowerCase, data, Map())

  def create(implicit db: Future[Database], ec: ExecutionContext): Future[RevedDocument[A]] = db.flatMap(_.createDoc(data))

  def create(id: String, docLogger: DocLogger = NopLogger)(implicit db: Future[Database], ec: ExecutionContext): Future[RevedDocument[A]] = {
    db.flatMap(_.createDocId(id, data, docLogger = docLogger))
  }

  def createViews(implicit db: Future[Database], ev: A =:= Views, ec: ExecutionContext): Future[RevedDocument[Views]] = {
    db.flatMap(_.createViews(this.asInstanceOf[NewDocument[Views]])) //cast will always work due to evidence parameter
  }
}

class DslNewDocSeq[A:RootJsonFormat](data: Seq[A]) {
  def create(implicit db: Future[Database], ec: ExecutionContext): Future[Seq[RevedDocument[A]]] = {
    db.flatMap(_.bulkPut(data.map(new NewDocument(_))))
  }
}
