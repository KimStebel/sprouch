package sprouch

import spray.json.RootJsonFormat
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

package object dsl {
  implicit def dataToDslDoc[A:RootJsonFormat](data:A):DslNewDocument[A] = {
    new DslNewDocument(data)
  }
  implicit def dslDoc[A:RootJsonFormat](doc:RevedDocument[A]):DslRevedDocument[A] = {
    new DslRevedDocument(doc.id, doc.rev, doc.data, doc.attachments)
  }
  def get[A](id:String)
      (implicit db:Future[Database],
                rjf:RootJsonFormat[A],
                executionContext:ExecutionContext):Future[RevedDocument[A]] = {
    db.flatMap(_.getDoc[A](id))
  }
  def get[A](doc:RevedDocument[A])
      (implicit db:Future[Database],
      		      rjf:RootJsonFormat[A],
      		      executionContext:ExecutionContext):Future[RevedDocument[A]] = {
    db.flatMap(_.getDoc[A](doc))
  }
}