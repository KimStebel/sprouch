package sprouch.dsl

import sprouch._
import java.util.UUID
import akka.dispatch.Future
import spray.json.RootJsonFormat

trait DslDocument[A] {
  
}

class DslNewDocument[A:RootJsonFormat](id:String, data:A) extends NewDocument[A](id, data) with DslDocument[A] {
  def this(data:A) = this(UUID.randomUUID.toString.toLowerCase, data)
  
  def create(implicit db:Future[Database]):Future[RevedDocument[A]] = db.flatMap(_.createDoc(data))  
  def create(id:String)(implicit db:Future[Database]):Future[RevedDocument[A]] = db.flatMap(_.createDoc(id, data))
}

class DslNewDocSeq[A:RootJsonFormat](data:Seq[A]) {
  def create(implicit db:Future[Database]):Future[Seq[RevedDocument[A]]] = {
    db.flatMap(_.bulkPut(data.map(new NewDocument(_))))
  }
}
