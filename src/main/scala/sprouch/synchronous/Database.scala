package sprouch.synchronous

import akka.util.Duration
import akka.dispatch.Future
import sprouch.Document
import sprouch.RevedDocument
import spray.json.RootJsonFormat
import akka.dispatch.Await
import sprouch.JsonProtocol.OkResponse
import sprouch.NewDocument
import sprouch.Attachment
import sprouch.Views
import spray.json.JsonFormat
import sprouch.ViewQueryFlag
import sprouch.StaleOption
import sprouch.StaleOption._
import sprouch.JsonProtocol.ViewResponse
import sprouch.JsonProtocol.AllDocsResponse

/**
  * This is just a synchronous wrapper around sprouch.Database.
  * Please look there for documentation. All the methods are identical,
  * except that they return A instead of Future[A].  
  */
class Database private (d:sprouch.Database, timeout:Duration) {
  
  private def await[A](f:Future[A]) = Await.result(f, timeout) 
  
  def bulkPut[A:RootJsonFormat](docs:Seq[Document[A]]):Seq[RevedDocument[A]] = await(d.bulkPut(docs)) 
  
  def delete():OkResponse = await(d.delete())
  
  def deleteDoc[A](doc:RevedDocument[A]):OkResponse = await(d.deleteDoc(doc))
  
  def getDoc[A:RootJsonFormat](id:String):RevedDocument[A] = await(d.getDoc(id))
  
  def getDoc[A:RootJsonFormat](doc:RevedDocument[A]):RevedDocument[A] = await(d.getDoc(doc)) 
  
  def createDoc[A:RootJsonFormat](doc:NewDocument[A]):RevedDocument[A] = await(d.createDoc(doc))
  
  def createDoc[A:RootJsonFormat](data:A):RevedDocument[A] = await(d.createDoc(data)) 

  def createDoc[A:RootJsonFormat](id:String, data:A):RevedDocument[A] = await(d.createDoc(id, data)) 
  
  def updateDoc[A:RootJsonFormat](doc:RevedDocument[A]):RevedDocument[A] = await(d.updateDoc(doc)) 
  
  def putAttachment[A](doc:RevedDocument[A], a:Attachment):RevedDocument[A] = await(d.putAttachment(doc, a))
  
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
      keyRange:Option[(String,String)] = None,
      keyDocIdRange:Option[(String,String)] = None,
      limit:Option[Int] = None,
      skip:Option[Int] = None,
      groupLevel:Option[Int] = None,
      stale:StaleOption = notStale
  ):ViewResponse[K,V] = await(d.queryView(designDocId,
      viewName,
      flags,
      key,
      keys,
      keyRange,
      keyDocIdRange,
      limit,
      skip,
      groupLevel,
      stale))
  
  def allDocs[V:RootJsonFormat](
      flags:Set[ViewQueryFlag] = ViewQueryFlag.default,
      key:Option[String] = None,
      keys:Seq[String] = Nil,
      keyRange:Option[(String,String)] = None,
      limit:Option[Int] = None,
      skip:Option[Int] = None,
      stale:StaleOption = notStale
  ):AllDocsResponse[V] = await(d.allDocs(
      flags,
      key,
      keys,
      keyRange,
      limit,
      skip,
      stale))  
}

object Database {
  private[synchronous] def apply(d:sprouch.Database, timeout:Duration):Database = new Database(d, timeout)
}