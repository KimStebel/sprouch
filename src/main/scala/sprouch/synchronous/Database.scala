package sprouch.synchronous

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import spray.json.{JsonFormat, RootJsonFormat}
import sprouch._
import JsonProtocol._
import StaleOption._
import scala.annotation.implicitNotFound
import spray.json.JsValue

/**
  * This is just a synchronous wrapper around sprouch.Database.
  * Please look there for documentation. All the methods are identical,
  * except that they return A instead of Future[A].  
  */
@implicitNotFound("You need to get a database object first and store it in an implicit val.")
class Database private (d:sprouch.Database, timeout:Duration) {
  
  private def await[A](f:Future[A]) = Await.result(f, timeout) 
  
  def revisions(doc:RevedDocument[_]):Seq[RevInfo] = await(d.revisions(doc))
  
  def bulkPut[A:RootJsonFormat](docs:Seq[Document[A]]):Seq[RevedDocument[A]] = await(d.bulkPut(docs)) 
  
  def delete():OkResponse = await(d.delete())
  
  def deleteDoc[A](doc:RevedDocument[A]):OkResponse = await(d.deleteDoc(doc))
  
  def getDoc[A:RootJsonFormat](id:String):RevedDocument[A] = await(d.getDoc[A](id))
  
  def getDoc[A:RootJsonFormat](doc:RevedDocument[A]):RevedDocument[A] = await(d.getDoc(doc)) 
  
  def createDoc[A:RootJsonFormat](doc:NewDocument[A]):RevedDocument[A] = await(d.createDoc(doc))
  
  def createDoc[A:RootJsonFormat](data:A):RevedDocument[A] = await(d.createDoc(data)) 

  def createDoc[A:RootJsonFormat](id:String, data:A):RevedDocument[A] = await(d.createDoc(id, data)) 
  
  def updateDoc[A:RootJsonFormat](doc:RevedDocument[A]):RevedDocument[A] = await(d.updateDoc(doc)) 
  
  def putAttachment[A:RootJsonFormat](doc:RevedDocument[A], a:Attachment):RevedDocument[A] = await(d.putAttachment(doc, a))
  
  def getAttachment(doc:Document[_], id:String):Attachment = await(d.getAttachment(doc, id)) 
  
  def deleteAttachment[A](doc:RevedDocument[A], a:Attachment):RevedDocument[A] = deleteAttachment(doc, a.id)
  
  def deleteAttachment[A](doc:RevedDocument[A], aid:String):RevedDocument[A] = await(d.deleteAttachment(doc, aid))
  
  def createViews(views:NewDocument[Views]):RevedDocument[Views] = await(d.createViews(views)) 
  
  def queryView[K:JsonFormat,V:JsonFormat](
      designDocId:String,
      viewName:String,
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
      stale:StaleOption = notStale
  ):ViewResponse[K,V] = await(d.queryView(designDocId,
      viewName,
      flags,
      key,
      keys,
      startKey,
      endKey,
      startKeyDocId,
      endKeyDocId,
      limit,
      skip,
      groupLevel,
      stale))
  
  def allDocs[V:RootJsonFormat](
      flags:Set[ViewQueryFlag] = ViewQueryFlag.default,
      key:Option[String] = None,
      keys:Seq[String] = Nil,
      startKey:Option[String] = None,
      endKey:Option[String] = None,
      limit:Option[Int] = None,
      skip:Option[Int] = None,
      stale:StaleOption = notStale
  ):AllDocsResponse[V] = await(d.allDocs(
      flags,
      key,
      keys,
      startKey,
      endKey,
      limit,
      skip,
      stale))  
}

object Database {
  private[synchronous] def apply(d:sprouch.Database, timeout:Duration):Database = new Database(d, timeout)
}