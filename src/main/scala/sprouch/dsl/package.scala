package sprouch

import spray.json.RootJsonFormat
import akka.dispatch.Future
import scala.annotation.implicitNotFound
import spray.json.JsonFormat
import spray.json.JsValue
import sprouch.StaleOption.notStale

package object dsl {
  def queryView[K,V](viewDocName:String, viewName:String,
      flags:Set[ViewQueryFlag] = ViewQueryFlag.default,
      key:Option[String] = None,
      keys:List[String] = Nil,
      startKey:Option[JsValue] = None,
      endKey:Option[JsValue] = None,
      startKeyDocId:Option[String] = None,
      endKeyDocId:Option[String] = None,
      limit:Option[Int] = None,
      skip:Option[Int] = None,
      groupLevel:Option[Int] = None,
      stale:StaleOption = notStale)(implicit db:Future[Database], jsfk:JsonFormat[K], jsfv:JsonFormat[V]) = {
    db.flatMap(_.queryView[K,V](viewDocName, viewName, flags, key, keys, startKey, endKey, startKeyDocId, endKeyDocId, limit, skip, groupLevel, stale))
  }
  implicit def dataToDslDoc[A:RootJsonFormat](data:A):DslNewDocument[A] = {
    new DslNewDocument(data)
  }
  implicit def dataToDslNewDocSeq[A:RootJsonFormat](data:Seq[A]):DslNewDocSeq[A] = {
    new DslNewDocSeq(data)
  }
  implicit def dataToDslRevedDocSeq[A:RootJsonFormat](data:Seq[RevedDocument[A]]):DslRevedDocSeq[A] = {
    new DslRevedDocSeq(data)
  }
  
  implicit def dslDoc[A:RootJsonFormat](doc:RevedDocument[A]):DslRevedDocument[A] = {
    new DslRevedDocument(doc.id, doc.rev, doc.data, doc.attachments)
  }
  implicit def newToDslDoc[A:RootJsonFormat](doc:NewDocument[A]):DslNewDocument[A] = {
    new DslNewDocument(doc.id, doc.data, doc.attachments)
  }
  def get[A](id:String)(implicit db:Future[Database], rjf:RootJsonFormat[A]):Future[RevedDocument[A]] = {
    db.flatMap(_.getDoc[A](id))
  }
  def get[A](doc:RevedDocument[A])(implicit db:Future[Database], rjf:RootJsonFormat[A]):Future[RevedDocument[A]] = {
    db.flatMap(_.getDoc[A](doc))
  }
  class EnhancedFuture[A](f:Future[A]) {
    def either = {
      f.map(Right(_)).recover {
        case e:Exception => Left(e)
      }
    }
    def option = {
      f.map(Some(_)).recover {
        case e:Exception => None
      }
    }
  }
  implicit def enhanceFuture[A](f: Future[A]) = new EnhancedFuture(f) 
}