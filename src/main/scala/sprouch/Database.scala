package sprouch

import java.util.UUID
import spray.httpx.marshalling.Marshaller
import spray.httpx.unmarshalling.Unmarshaller
import akka.actor.ActorRef
import spray.http.HttpMethods.HEAD
import spray.httpx.SprayJsonSupport._
import akka.dispatch.Future
import spray.json.RootJsonFormat
import spray.httpx.RequestBuilding.RequestBuilder
import spray.http.HttpRequest
import JsonProtocol._
import spray.json.JsonFormat
import StaleOption._
import ViewQueryFlag._
import scala.annotation.implicitNotFound
import spray.json.{JsValue, JsonWriter, JsObject, JsArray}
import spray.httpx.RequestBuilding._

trait DbUriBuilder extends UriBuilder {
  def name:String
  protected[this] def dbUri:String = dbUri(name)
  
}

/**
  * Supports CRUD operations on documents and attachments,
  * creating and querying views, bulk get, update, and delete operations.  
  */
class Database private[sprouch](val name:String, protected[this] val pipelines:Pipelines, protected[this] val config:Config)
    extends DbUriBuilder with SearchModule with DbChangesModule {
  import pipelines._
  
  private def docUri(doc:Document[_]):String = docUri(doc.id)
  private def docUri(id:String) = path(name, id)
  private def docUriRev(doc:RevedDocument[_]) = docUri(doc) + "?rev=" + doc.rev
  private def attachmentUriRev(doc:RevedDocument[_], aid:String):String =
    attachmentUri(doc, aid) + "?rev=" + doc.rev
  private def attachmentUri(doc:Document[_], aid:String) = docUri(doc) + sep + encode(aid)
  private def designDocUri(doc:Document[_]) = path(name, "_design", doc.id)
  private def keyValue[A](key:String)(value:A)(implicit aFormat:JsonFormat[A]) =
    key + "=" + encode(aFormat.write(value).toString)
  private def viewQueryUri(
      designDocId:String,
      viewName:String,
      kvs:List[String]) =
    path(name, "_design", designDocId, "_view", viewName) + query(kvs:_*)
  
  private def allDocsUri(kvs:List[String]) = {
    path(name, "_all_docs") + query(kvs:_*)
  }
  private def bulkUri:String = path(name, "_bulk_docs")
  private def revisionsUri(id:String) = docUri(id) + "?revs_info=true"
  
  def shards(docLogger:DocLogger = NopLogger):Future[ShardsResponse] = {
    val p = pipeline[ShardsResponse](docLogger = docLogger)
    p(Get(path(name, "_shards")))
  }
  
  def shardForDoc(id:String, docLogger:DocLogger = NopLogger):Future[ShardsDocIdResponse] = {
    val p = pipeline[ShardsDocIdResponse](docLogger = docLogger)
    p(Get(path(name, "_shards", id)))
  }
  
  
  def security(docLogger:DocLogger = NopLogger):Future[SecuritySettings] = {
    val p = pipeline[SecuritySettings](docLogger = docLogger)
    p(Get(path(name, "_security")))
  }
  /**
   * Retrives old revisions of a document.
   */
  def revisions(doc:RevedDocument[_], docLogger:DocLogger = NopLogger):Future[Seq[RevInfo]] = {
    val p = pipeline[RevsInfo](docLogger = docLogger)
    p(Get(revisionsUri(doc.id))).map(_._revs_info)
  }
  
  /**
    * Creates or updates Documents in Bulk.
    */
  def bulkPut[A:RootJsonFormat](docs:Seq[Document[A]], docLogger:DocLogger=NopLogger):Future[Seq[RevedDocument[A]]] = {
    val p = pipeline[Seq[CreateResponse]](docLogger = docLogger)
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
  def getDoc[A:RootJsonFormat](id:String, docLogger:DocLogger = NopLogger):Future[RevedDocument[A]] = {
    val p = pipeline[RevedDocument[A]](docLogger = docLogger)
    p(Get(docUri(id)))
  }
  
  /**
    * Retrieves a document. If the document is still current, the document is not transmitted again and doc is returned.
    */
  def getDocAgain[A:RootJsonFormat](doc:RevedDocument[A], docLogger:DocLogger = NopLogger):Future[RevedDocument[A]] = {
    val p = pipeline[RevedDocument[A]](etag = Some(doc.rev), docLogger = docLogger)
    p(Get(docUri(doc.id))).recover {
      case SprouchException(e) if e.status == 304 => doc
    }
  }
  
  /**
    * Creates a new document with the id given in the doc parameter.
    */
  def createDoc[A:RootJsonFormat](doc:NewDocument[A], docLogger:DocLogger = NopLogger):Future[RevedDocument[A]] = {
    val p = pipeline[CreateResponse]
    val response = p(Put(docUri(doc), doc))
    response.map(cr => doc.setRev(cr.rev))
  }
  
  /**
    * Creates a new Document with the id set by CouchDB and returned in the RevedDocument that is returned.
    */
  def createDocData[A:RootJsonFormat](data:A, docLogger:DocLogger=NopLogger):Future[RevedDocument[A]] = {
    createDoc(new NewDocument(data), docLogger)
  }

  /**
    * Creates a new document with the given id.
    */
  def createDocId[A:RootJsonFormat](id:String, data:A, docLogger:DocLogger = NopLogger):Future[RevedDocument[A]] = {
    createDoc(new NewDocument(id, data, Map()), docLogger)
  }
  
  /**
    * Updates a document.
    */
  def updateDoc[A:RootJsonFormat](doc:RevedDocument[A], docLogger:DocLogger = NopLogger):Future[RevedDocument[A]] = {
    val p = pipeline[CreateResponse](docLogger = docLogger)
    val response = p(Put(docUriRev(doc), doc))
    response.map(cr => doc.setRev(cr.rev))
  }
  
  /**
    * Creates or updates an attachment.
    */
  def putAttachment[A:RootJsonFormat](doc:RevedDocument[A], a:Attachment, docLogger:DocLogger = NopLogger):Future[RevedDocument[A]] = {
    val p = pipeline[CreateResponse](docLogger = docLogger)
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
  def deleteAttachment[A](doc:RevedDocument[A], a:Attachment, docLogger:DocLogger = NopLogger):Future[RevedDocument[A]] = { 
    deleteAttachmentId(doc, a.id, docLogger)
  }
  
  /**
    * Deletes an attachment.
    */
  def deleteAttachmentId[A](doc:RevedDocument[A], aid:String, docLogger:DocLogger = NopLogger):Future[RevedDocument[A]] = {
    val p = pipeline[CreateResponse](docLogger = docLogger)
    p(Delete(attachmentUriRev(doc, aid))).map(cr => 
      new RevedDocument(id = doc.id, rev = cr.rev, doc.data, doc.attachments - aid))
  }
  
  def createIndexes(indexes:NewDocument[Indexes], docLogger:DocLogger = NopLogger):Future[RevedDocument[Indexes]] = {
    val p = pipeline[CreateResponse](docLogger = docLogger)
    val response = p(Put(designDocUri(indexes), indexes))
    response.map(cr => indexes.setRev(cr.rev))
  }
  
  /**
    * Creates a view document containing one or more views. See the Views class for details.
    */
  def createViews(views:NewDocument[Views]):Future[RevedDocument[Views]] = {
    val p = pipeline[CreateResponse]
    val response = p(Put(designDocUri(views), views))
    response.map(cr => views.setRev(cr.rev))
  }
  
  def createDesign(designDoc:NewDocument[DesignDoc], docLogger:DocLogger = NopLogger):Future[RevedDocument[DesignDoc]] = {
    val p = pipeline[CreateResponse](docLogger = docLogger)
    val response = p(Put(designDocUri(designDoc), designDoc))
    response.map(cr => designDoc.setRev(cr.rev))
  }
  
  def show(designDoc:String, showName:String, docId:String, query:String, docLogger:DocLogger = NopLogger) = {
    val p = pipelines.pipelineWithoutUnmarshal(docLogger = docLogger)
    p(Get(path(name, "_design", designDoc, "_show", showName, docId)+"?"+query))
  }
  
  def list(designDoc:String, listName:String, viewName:String, docLogger:DocLogger = NopLogger) = {
    val p = pipelines.pipelineWithoutUnmarshal(docLogger = docLogger)
    p(Get(path(name, "_design", designDoc, "_list", listName, viewName)))
  }
  
  def viewQueries(designDocId:String, viewName:String, queries:Seq[ViewQuery], docLogger:DocLogger = NopLogger):Future[Seq[JsObject]] = {
    val p = pipeline[Map[String,JsValue]](docLogger = docLogger)
    val body = Map("queries" -> queries)
    val resp = p(Post(path(name, "_design", designDocId, "_view", viewName), body))
    resp.map(m => m("results").asInstanceOf[JsArray].elements.toSeq.asInstanceOf[Seq[JsObject]])
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
      startKey:Option[JsValue] = None,
      endKey:Option[JsValue] = None,
      startKeyDocId:Option[String] = None,
      endKeyDocId:Option[String] = None,
      limit:Option[Int] = None,
      skip:Option[Int] = None,
      groupLevel:Option[Int] = None,
      stale:StaleOption = notStale,
      docLogger:DocLogger = NopLogger
  ):Future[ViewResponse[K,V]] = { 
    val p = pipeline[ViewResponse[K,V]](docLogger = docLogger)
    val flagsWithImplicitGroup:Set[ViewQueryFlag] = flags ++ Set(group).filter(_ => !keys.isEmpty)
    val kvs = 
      flagsWithImplicitGroup.toList.map(f => keyValue(f.toString)(true)) ++
      (ViewQueryFlag.all -- Set(group).filter(_ => !flags.contains(reduce))).diff(flagsWithImplicitGroup).toList.map(f => keyValue(f.toString)(false)) ++ 
      List(
        key.map(keyValue("key")),
        Option(keys).filter(!_.isEmpty).map(keyValue("keys")),
        startKey.map(keyValue("startkey")),
        endKey.map(keyValue("endkey")),
        startKeyDocId.map(keyValue("startkey_docid")),
        endKeyDocId.map(keyValue("endkey_docid")),
        limit.map(keyValue("limit")),
        skip.map(keyValue("skip")),
        groupLevel.map(keyValue("group_level")),
        Option(stale).filter(notStale !=).map("stale=" +)
      ).flatten
    
    val uri = viewQueryUri(
        designDocId,
        viewName,
        kvs)
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
      startKey:Option[String] = None,
      endKey:Option[String] = None,
      limit:Option[Int] = None,
      skip:Option[Int] = None,
      stale:StaleOption = notStale,
      docLogger:DocLogger = NopLogger
  ):Future[AllDocsResponse[V]] = { 
    val p = pipeline[AllDocsResponse[V]](docLogger = docLogger)
    val flagsWithGroupWithoutReduce:Set[ViewQueryFlag] = (flags ++ Set(group).filter(_ => !keys.isEmpty)) -- Set(reduce, group)
    val kvs = 
      (flagsWithGroupWithoutReduce -- Set(inclusive_end)).toList.map(f => keyValue(f.toString)(true)) ++
      (ViewQueryFlag.all.diff(Set(update_seq, descending, include_docs, reduce, group))).diff(flagsWithGroupWithoutReduce).toList.map(f => keyValue(f.toString)(false)) ++ 
      List(
        key.map(keyValue("key")),
        Option(keys).filter(!_.isEmpty).map(keyValue("keys")),
        startKey.map(keyValue("startkey")),
        endKey.map(keyValue("endkey")),
        limit.map(keyValue("limit")),
        skip.map(keyValue("skip")),
        Option(stale).filter(notStale !=).map("stale=" +)
      ).flatten
    
    val uri = allDocsUri(kvs)
    p(Get(uri))
  }
  
}