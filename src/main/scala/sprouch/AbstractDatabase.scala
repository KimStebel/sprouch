package sprouch

import akka.dispatch.Future
import JsonProtocol._
import spray.json.RootJsonFormat
import spray.json.JsonFormat
import StaleOption._

trait AbstractDatabase {

  def revisions(doc:RevedDocument[_]):Future[Seq[RevInfo]]
  def bulkPut[A:RootJsonFormat](docs:Seq[Document[A]]):Future[Seq[RevedDocument[A]]]
  def delete():Future[OkResponse]
  def deleteDoc[A](doc:RevedDocument[A]):Future[OkResponse]  
  def getDoc[A:RootJsonFormat](id:String):Future[RevedDocument[A]]
  def getDoc[A:RootJsonFormat](doc:RevedDocument[A]):Future[RevedDocument[A]]
  def createDoc[A:RootJsonFormat](doc:NewDocument[A]):Future[RevedDocument[A]]
  def createDoc[A:RootJsonFormat](data:A):Future[RevedDocument[A]]
  def createDoc[A:RootJsonFormat](id:String, data:A):Future[RevedDocument[A]]
  def updateDoc[A:RootJsonFormat](doc:RevedDocument[A]):Future[RevedDocument[A]]
  def putAttachment[A](doc:RevedDocument[A], a:Attachment):Future[RevedDocument[A]]
  def getAttachment(doc:Document[_], id:String):Future[Attachment]
  def deleteAttachment[A](doc:RevedDocument[A], a:Attachment):Future[RevedDocument[A]]
  def deleteAttachment[A](doc:RevedDocument[A], aid:String):Future[RevedDocument[A]]
  def createViews(views:NewDocument[Views]):Future[RevedDocument[Views]]
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
  ):Future[ViewResponse[K,V]]
  def allDocs[V:RootJsonFormat](
      flags:Set[ViewQueryFlag] = ViewQueryFlag.default,
      key:Option[String] = None,
      keys:Seq[String] = Nil,
      keyRange:Option[(String,String)] = None,
      limit:Option[Int] = None,
      skip:Option[Int] = None,
      stale:StaleOption = notStale
  ):Future[AllDocsResponse[V]]
  
}