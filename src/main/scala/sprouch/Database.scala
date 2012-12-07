package sprouch

import java.util.UUID
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller
import akka.actor.ActorRef
import spray.http.HttpMethods.HEAD
import spray.client.HttpConduit
import HttpConduit.{Delete, Get, Put}
import spray.httpx.SprayJsonSupport._
import scala.concurrent.Future
import spray.json.RootJsonFormat
import spray.httpx.RequestBuilding.RequestBuilder
import spray.http.HttpRequest
import JsonProtocol._
import spray.json.JsonFormat
import scala.concurrent.ExecutionContext.Implicits.global


class Database private[sprouch](val name:String, pipelines:Pipelines) extends UriBuilder {
  import pipelines._
  
  private def dbUri:String = dbUri(name)
  private def docUri(doc:Document[_]):String = docUri(doc.id)
  private def docUri(id:String) = path(name, id)
  private def docUriRev(doc:RevedDocument[_]) = docUri(doc) + "?rev=" + doc.rev
  private def attachmentUriRev(doc:RevedDocument[_], a:Attachment):String =
    attachmentUri(doc, a.id) + "?rev=" + doc.rev
  private def attachmentUri(doc:Document[_], aid:String) = docUri(doc) + sep + encode(aid)
  private def viewsUri(views:Document[Views]) = path(name, "_design", views.id)
  private def keyValue[A](key:String)(value:A)(implicit aFormat:JsonFormat[A]) = key + "=" + encode(aFormat.write(value).toString)
  private def query(kv:String*) = {
    if (kv.isEmpty) "" else {
      "?" + kv.mkString("&")
    }
  }
  private def viewQueryUri(
      designDocId:String,
      viewName:String,
      kvs:List[String]) = {
    path(name, "_design", designDocId, "_view", viewName) + query(kvs:_*)
  }
  
  def delete() = {
    val p = pipeline[OkResponse]
    p(Delete(dbUri))
  }
  
  def deleteDoc[A](doc:RevedDocument[A]):Future[OkResponse] = {
    val p = pipeline[OkResponse]
    p(Delete(docUriRev(doc)))
  }
  
  def getDoc[A:RootJsonFormat](id:String):Future[RevedDocument[A]] = {
    val p = pipeline[RevedDocument[A]]
    p(Get(docUri(id)))
  }
  
  def allDocs[A:RootJsonFormat](from:Option[String] = None, to:Option[String] = None, limit:Option[Int] = None) = {
    val uri = {
      val options = List(
          Some("include_docs=true"),
          from.map(keyValue("startkey")),
          to.map(keyValue("endkey")),
          limit.map(keyValue("limit"))
      ).flatten
      val query = if (options.isEmpty) "" else "?" + options.mkString("&")
      dbUri + "/_all_docs" + query
    }
    val p = pipeline[AllDocsResponse[A]]
    p(Get(uri))
  }
  
  def createDoc[A:RootJsonFormat](doc:NewDocument[A]):Future[RevedDocument[A]] = {
    val p = pipeline[CreateResponse]
    val response = p(Put(docUri(doc), doc))
    response.map(cr => doc.setRev(cr.rev))
  }
  
  def createDoc[A:RootJsonFormat](data:A):Future[RevedDocument[A]] = {
    createDoc(new NewDocument(data))
  }

  def createDoc[A:RootJsonFormat](id:String, data:A):Future[RevedDocument[A]] = {
    createDoc(new NewDocument(id, data, Map()))
  }
  
  def updateDoc[A:RootJsonFormat](doc:RevedDocument[A]):Future[RevedDocument[A]] = {
    val p = pipeline[CreateResponse]
    val response = p(Put(docUriRev(doc), doc))
    response.map(cr => doc.setRev(cr.rev))
  }
  
  def putAttachment[A](doc:RevedDocument[A], a:Attachment):Future[RevedDocument[A]] = {
    val p = pipeline[CreateResponse]
    p(Put(attachmentUriRev(doc, a), a.data)).map(cr => doc.setRev(cr.rev))
  }
  
  def getAttachment(doc:Document[_], id:String):Future[Attachment] = {
    val p = pipeline[Array[Byte]]
    p(Get(attachmentUri(doc, id))).map(array => new Attachment(id, array))
  }
  
  def deleteAttachment[A](doc:RevedDocument[A], a:Attachment):Future[RevedDocument[A]] = {
    val p = pipeline[CreateResponse]
    p(Delete(attachmentUriRev(doc, a))).map(cr => doc.setRev(cr.rev))
  }
  
  def createViews(views:NewDocument[Views]):Future[RevedDocument[Views]] = {
    val p = pipeline[CreateResponse]
    val response = p(Put(viewsUri(views), views))
    response.map(cr => views.setRev(cr.rev))
  }
  
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
  ):Future[ViewResponse[K,V]] = { 
    val p = pipeline[ViewResponse[K,V]]
    val flagsWithImplicitGroup:Set[ViewQueryFlag] = flags ++ Set(group).filter(_ => !keys.isEmpty)
    val kvs = 
      flagsWithImplicitGroup.toList.map(f => keyValue(f.toString)(true)) ++
      ViewQueryFlag.all.diff(flagsWithImplicitGroup).toList.map(f => keyValue(f.toString)(false)) ++ 
      List(
        key.map(keyValue("key")),
        Option(keys).filter(!_.isEmpty).map(keyValue("keys")),
        keyRange.map { case (from, _) => keyValue("startkey")(from) },
        keyRange.map { case (_, to) => keyValue("endkey")(to) },
        keyDocIdRange.map { case (from, _) => keyValue("startkey_docid")(from) },
        keyDocIdRange.map { case (_, to) => keyValue("endkey_docid")(to) },
        limit.map(keyValue("limit")),
        skip.map(keyValue("skip")),
        groupLevel.map(keyValue("group_level")),
        Option(stale).filter(notStale !=).map("stale=" +)
      ).flatten
    
    val uri = viewQueryUri(
        designDocId,
        viewName,
        kvs)
    println("URI: " + uri)
    p(Get(uri))
  }

}