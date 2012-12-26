package sprouch

import java.util.UUID
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller
import akka.actor.ActorRef
import spray.http.HttpMethods.HEAD
import spray.client.HttpConduit
import HttpConduit.{Post, Delete, Get, Put}
import spray.httpx.SprayJsonSupport._
import scala.concurrent.Future
import spray.json.RootJsonFormat
import spray.httpx.RequestBuilding.RequestBuilder
import spray.http.HttpRequest
import JsonProtocol._
import spray.json.JsonFormat
import StaleOption._
import ViewQueryFlag._

/**
  * Supports CRUD operations on documents and attachments,
  * creating and querying views, bulk get, update, and delete operations.  
  */
class Database private[sprouch](val name:String, pipelines:Pipelines) extends UriBuilder {
  import pipelines._
  import as.dispatcher
  
  private def dbUri:String = dbUri(name)
  private def docUri(doc:Document[_]):String = docUri(doc.id)
  private def docUri(id:String) = path(name, id)
  private def docUriRev(doc:RevedDocument[_]) = docUri(doc) + "?rev=" + doc.rev
  private def attachmentUriRev(doc:RevedDocument[_], aid:String):String =
    attachmentUri(doc, aid) + "?rev=" + doc.rev
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
  private def allDocsUri(kvs:List[String]) = {
    path(name, "_all_docs") + query(kvs:_*)
  }
  private def bulkUri:String = path(name, "_bulk_docs")
  private def revisionsUri(id:String) = docUri(id) + "?revs_info=true"
  
  /**
   * Retrives old revisions of a document.
   */
  def revisions(doc:RevedDocument[_]):Future[Seq[RevInfo]] = {
    val p = pipeline[RevsInfo]
    p(Get(revisionsUri(doc.id))).map(_._revs_info)
  }
  
  /**
    * Creates or updates Documents in Bulk.
    */
  def bulkPut[A:RootJsonFormat](docs:Seq[Document[A]]):Future[Seq[RevedDocument[A]]] = {
    val p = pipeline[Seq[CreateResponse]]
    p(Post(bulkUri, BulkPut(docs))).map(crs => {
      crs.zip(docs).map { case (cr, doc) => doc.setRev(cr.rev) }
    })
  }
  
  /**
    * Deletes the entire database.
    */
  def delete() = {
    val p = pipeline[OkResponse]
    p(Delete(dbUri))
  }
  
  /**
    * Deletes a document.
    */
  def deleteDoc[A](doc:RevedDocument[A]):Future[OkResponse] = {
    val p = pipeline[OkResponse]
    p(Delete(docUriRev(doc)))
  }
  
  /**
    * Retrieves a document.
    */
  def getDoc[A:RootJsonFormat](id:String):Future[RevedDocument[A]] = {
    val p = pipeline[RevedDocument[A]]
    p(Get(docUri(id)))
  }
  
  /**
    * Retrieves a document. If the document is still current, the document is not transmitted again and doc is returned.
    */
  def getDoc[A:RootJsonFormat](doc:RevedDocument[A]):Future[RevedDocument[A]] = {
    val p = pipeline[RevedDocument[A]](etag = Some(doc.rev))
    p(Get(docUri(doc.id))).recover {
      case SprouchException(e) if e.status == 304 => doc
    }
  }
  
  /**
    * Creates a new document with the id given in the doc parameter.
    */
  def createDoc[A:RootJsonFormat](doc:NewDocument[A]):Future[RevedDocument[A]] = {
    val p = pipeline[CreateResponse]
    val response = p(Put(docUri(doc), doc))
    response.map(cr => doc.setRev(cr.rev))
  }
  
  /**
    * Creates a new Document with the id set by CouchDB and returned in the RevedDocument that is returned.
    */
  def createDoc[A:RootJsonFormat](data:A):Future[RevedDocument[A]] = {
    createDoc(new NewDocument(data))
  }

  /**
    * Creates a new document with the given id.
    */
  def createDoc[A:RootJsonFormat](id:String, data:A):Future[RevedDocument[A]] = {
    createDoc(new NewDocument(id, data, Map()))
  }
  
  /**
    * Updates a document.
    */
  def updateDoc[A:RootJsonFormat](doc:RevedDocument[A]):Future[RevedDocument[A]] = {
    val p = pipeline[CreateResponse]
    val response = p(Put(docUriRev(doc), doc))
    response.map(cr => doc.setRev(cr.rev))
  }
  
  /**
    * Creates or updates an attachment.
    */
  def putAttachment[A:RootJsonFormat](doc:RevedDocument[A], a:Attachment):Future[RevedDocument[A]] = {
    val p = pipeline[CreateResponse]
    for {
      _ <- p(Put(attachmentUriRev(doc, a.id), a.data))
      doc2 <- getDoc[A](doc.id)
    } yield doc2
  }
  
  /**
    * Retrieves an attachment.
    */
  def getAttachment(doc:Document[_], id:String):Future[Attachment] = {
    val p = pipeline[Array[Byte]]
    p(Get(attachmentUri(doc, id))).map(array => new Attachment(id, array))
  }
  
  /**
    * Deletes an attachment.
    */
  def deleteAttachment[A](doc:RevedDocument[A], a:Attachment):Future[RevedDocument[A]] = deleteAttachment(doc, a.id)
  
  /**
    * Deletes an attachment.
    */
  def deleteAttachment[A](doc:RevedDocument[A], aid:String):Future[RevedDocument[A]] = {
    val p = pipeline[CreateResponse]
    p(Delete(attachmentUriRev(doc, aid))).map(cr => 
      new RevedDocument(id = doc.id, rev = cr.rev, doc.data, doc.attachments - aid))
  }
  
  /**
    * Creates a view document containing one or more views. See the Views class for details.
    */
  def createViews(views:NewDocument[Views]):Future[RevedDocument[Views]] = {
    val p = pipeline[CreateResponse]
    val response = p(Put(viewsUri(views), views))
    response.map(cr => views.setRev(cr.rev))
  }
  
  /**
    * Queries a view. Most parameters are not documented here, since they are already documented in CouchDB's documentation.
    * 
    * @tparam K Type of the keys of the view.
    *  In case of a reduce view without the group option, this will be Null.
    *  There needs to be an implicit JsonFormat[K] in scope.
    * @tparam V Type of the values of the view.
    *  There needs to be an implicit JsonFormat[V] in scope.
    * @param designDocId id of the document containing the view.
    *  This is the same document you passed to createViews.
    * @param name of the view inside the document given by designDocId.
    *  
    */
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
  
  /**
   * Retrieves all or a range of Documents. The parameters are not documented here, since they are already documented in CouchDB's documentation.
   * 
   * @tparam V Type of the documents.
   *  There needs to be an implicit JsonFormat[V] in scope.
   * 
   */
  def allDocs[V:RootJsonFormat](
      flags:Set[ViewQueryFlag] = ViewQueryFlag.default,
      key:Option[String] = None,
      keys:Seq[String] = Nil,
      keyRange:Option[(String,String)] = None,
      limit:Option[Int] = None,
      skip:Option[Int] = None,
      stale:StaleOption = notStale
  ):Future[AllDocsResponse[V]] = { 
    val p = pipeline[AllDocsResponse[V]]
    val flagsWithGroupWithoutReduce:Set[ViewQueryFlag] = (flags ++ Set(group).filter(_ => !keys.isEmpty) ++ Set(include_docs)) -- Set(reduce, group)
    val kvs = 
      flagsWithGroupWithoutReduce.toList.map(f => keyValue(f.toString)(true)) ++
      (ViewQueryFlag.all.diff(Set(reduce, group))).diff(flagsWithGroupWithoutReduce).toList.map(f => keyValue(f.toString)(false)) ++ 
      List(
        key.map(keyValue("key")),
        Option(keys).filter(!_.isEmpty).map(keyValue("keys")),
        keyRange.map { case (from, _) => keyValue("startkey")(from) },
        keyRange.map { case (_, to) => keyValue("endkey")(to) },
        limit.map(keyValue("limit")),
        skip.map(keyValue("skip")),
        Option(stale).filter(notStale !=).map("stale=" +)
      ).flatten
    
    val uri = allDocsUri(kvs)
    p(Get(uri))
  }
  
}