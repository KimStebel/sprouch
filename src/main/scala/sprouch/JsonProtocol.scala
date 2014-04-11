package sprouch

import spray.http._
import HttpMethods._
import spray.httpx.encoding.{Gzip, Deflate}
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling.Unmarshaller
import spray.io._
import spray.json._
import spray.util._
import java.util.UUID

/**
 * contains classes needed to model the protocol used by CouchDB
 * and implicit vals and defs of type JsonFormat to convert Scala types to and from JSON.
 */

trait JsonProtocolTrait extends DefaultJsonProtocol with SprouchJsonProtocol with ChangesJsonProtocol

object JsonProtocol extends JsonProtocolTrait

trait SprouchJsonProtocol {
  self:DefaultJsonProtocol =>
  trait Id {
    val id:String
  }
  trait Rev {
    val rev:String
  }
  case class ApiKeyResponse(ok:Boolean, key:String, password:String)
  implicit val apiKeyResponseFormat = jsonFormat3(ApiKeyResponse)
  case class Analyzer(name:String, default:Option[String], fields:Option[Map[String, String]])
  implicit val analyzerFormat = jsonFormat3(Analyzer)
  case class Index(index:String, analyzer:Option[Analyzer])
  implicit val indexFormat = jsonFormat2(Index)
  case class Indexes(indexes:Map[String,Index])
  implicit val indexesFormat = jsonFormat1(Indexes)

  case class RevInfo(rev:String, status:String)
  implicit val revInfoFormat = jsonFormat2(RevInfo)
  case class RevsInfo(_revs_info:Seq[RevInfo])
  implicit val revsInfoFormat = jsonFormat1(RevsInfo)
  implicit val nullFormat:JsonFormat[Null] = new JsonFormat[Null] {
    override def read(js:JsValue) = if (js == JsNull) null else throw new Exception("null expected")
    override def write(n:Null) = JsNull
  }
  implicit val mapReduceFormat = jsonFormat3(
      (map:String, reduce:Option[String], dbcopy:Option[String]) => MapReduce(map, reduce, dbcopy))
  implicit val viewsFormat = jsonFormat1(Views)
  case class ViewRow[K,V](key:K, value:V, doc:Option[JsValue]) {
    def docAs[A:JsonFormat] = doc.map(implicitly[JsonFormat[A]].read)
  }
  implicit def viewRowFormat[K:JsonFormat,V:JsonFormat] = jsonFormat3(ViewRow[K,V])
  case class ViewResponse[K,V](rows:List[ViewRow[K,V]]) {
    def docs[A:JsonFormat] = rows.flatMap(_.docAs[A])
    def values = rows.map(_.value)
    def keys = rows.map(_.key)
  }
  implicit def viewResponseFormat[K:JsonFormat,V:JsonFormat] = jsonFormat1(ViewResponse[K,V])
  implicit val attachmentStubFormat = jsonFormat3(AttachmentStub)
  case class Revision(rev:String)
  implicit val revisionFormat = jsonFormat1(Revision)
  case class AllDocsRow[A](id:String, key:String, value:Revision, doc:Option[A])
  implicit def allDocsRowFormat[A:RootJsonFormat] = jsonFormat4(AllDocsRow[A])
  case class AllDocsResponse[A](total_rows:Int, offset:Int, rows:Seq[AllDocsRow[A]])
  implicit def allDocsResponseFormat[A:RootJsonFormat] = jsonFormat3(AllDocsResponse[A])
  case class OkResponse(ok:Boolean)
  case class CreateResponse(ok:Option[Boolean], id:String, rev:String)
  case class ErrorResponse(status:Int, body:Option[ErrorResponseBody])
  case class ErrorResponseBody(error:String, reason:Option[String])
  case object Empty
  
  implicit val emptyFormat = new RootJsonFormat[Empty.type] {
    def read(js:JsValue) = Empty
    def write(e:Empty.type) = new JsObject(Map())
  }
  implicit val getDbResponseFormat = jsonFormat10(GetDbResponse)
  implicit val okResponseFormat = jsonFormat1(OkResponse)
  implicit val createResponseFormat = jsonFormat3(CreateResponse)
  implicit val errorResponseFormat = jsonFormat2(ErrorResponseBody)
  implicit def revedDocJsonFormat[A:RootJsonFormat]:RootJsonFormat[RevedDocument[A]] = new RevedDocFormat[A]
  implicit def newDocJsonFormat[A:RootJsonFormat]:RootJsonFormat[NewDocument[A]] = new NewDocFormat[A]
  implicit def documentFormat[A:RootJsonFormat]:RootJsonFormat[Document[A]] = new AnyDocFormat[A]
  implicit object UuidJsonFormat extends RootJsonFormat[UUID] {
    def write(id:UUID) = JsString(id.toString)
    def read(value: JsValue) = value match {
      case JsString(s) => UUID.fromString(s)
      case _ => deserializationError("UUID string expected")
    }
  }
  
  implicit object JsObjectJsonFormat extends RootJsonFormat[JsObject] {
    override def write(in:JsObject) = in
    override def read(out: JsValue) = out.asInstanceOf[JsObject]
  }
  
  case class GetDbResponse(
    db_name:String,
    doc_count:Int,
    doc_del_count:Int,
    update_seq:Either[String, Int],
    purge_seq:Int,
    compact_running:Boolean,
    disk_size:Int,
    instance_start_time:String,
    disk_format_version:Int,
    committed_update_seq:Option[Int]
  )

  class AnyDocFormat[A:RootJsonFormat] extends DocFormat[A, Document[A]] {
    override def otherFields(doc:Document[A]):Map[String, JsValue] = doc match {
      case _:NewDocument[_] => Map()
      case doc:RevedDocument[_] => {
        val stringFormat = implicitly[JsonFormat[String]]
        Map("_rev" -> stringFormat.write(doc.rev))
      }
    }
    
    override def makeB(fields:Map[String,JsValue], id:String, data:A, attachments:Map[String,AttachmentStub]) = {
      val stringFormat = implicitly[JsonFormat[String]]
      val rev = fields.get("_rev").map(stringFormat.read)
      rev match {
        case Some(_rev) => new RevedDocument(id, _rev, data, attachments)
        case None => new NewDocument(id, data, attachments)
      }
    }
  } 
  
  abstract class DocFormat[A:RootJsonFormat, B <: Document[A]] extends RootJsonFormat[B] {
    
    def otherFields(doc:B):Map[String,JsValue]
    def makeB(fields:Map[String,JsValue], id:String, data:A, attachments:Map[String,AttachmentStub]):B
    
    override def write(doc:B) = {
      val stringFormat = implicitly[JsonFormat[String]]
      val dataFormat = implicitly[RootJsonFormat[A]]
      val dataJson = dataFormat.write(doc.data)
      dataJson match {
        case o:JsObject => {
          val dataFields = o.fields
          val docFields = Map(
              "_id" -> stringFormat.write(doc.id),
              "_attachments" -> JsObject(doc.attachments.map{ case (k,v) => k -> attachmentStubFormat.write(v)})
          )
          JsObject(dataFields ++ docFields ++ otherFields(doc))
        }
        case js => throw new Exception("data does not serialize to json object: " + js)
      }
      
    }
    override def read(value: JsValue):B = value match {
      case o:JsObject => {
        val fields = o.fields
        val stringFormat = implicitly[JsonFormat[String]]
        val dataFormat = implicitly[RootJsonFormat[A]]
        val _id = stringFormat.read(fields("_id"))
        val attachments = fields.get("_attachments").toList.flatMap {
          case JsObject(as) => as.map { case (k,v) => k -> attachmentStubFormat.read(v) }
          case _ => deserializationError("json array expected")
        }.toMap
        val data =  dataFormat.read(o)
        makeB(fields, _id, data, attachments)
      }
      case _ => deserializationError("json object expected")
    }
  }
  
  class RevedDocFormat[A:RootJsonFormat] extends DocFormat[A,RevedDocument[A]] {
    override def otherFields(doc:RevedDocument[A]) = {
      val stringFormat = implicitly[JsonFormat[String]]
      Map("_rev" -> stringFormat.write(doc.rev))
    }
    override def makeB(fields:Map[String,JsValue], id:String, data:A, attachments:Map[String,AttachmentStub]) = {
      val stringFormat = implicitly[JsonFormat[String]]
      val _rev = stringFormat.read(fields("_rev"))    
      new RevedDocument(id, _rev, data, attachments)
    }
  }
  class NewDocFormat[A:RootJsonFormat] extends DocFormat[A,NewDocument[A]] {
    override def otherFields(doc:NewDocument[A]) = Map()
    override def makeB(fields:Map[String,JsValue], id:String, data:A, attachments:Map[String,AttachmentStub]) = 
      new NewDocument(id, data, attachments)
    
  }
  
  case class BulkPut[A](docs:Seq[Document[A]])
  implicit def bulkPutFormat[A:RootJsonFormat] = jsonFormat1(BulkPut[A])
  
  implicit def toJsValue[A:JsonFormat](a:A) = implicitly[JsonFormat[A]].write(a)
  implicit def pairToJsPair[A:JsonFormat](ap:(A,A)) = {
    val f = implicitly[JsonFormat[A]]
    (f.write(ap._1),f.write(ap._2))
  }
  
  case class SearchResultRow(id:String, order:Seq[Option[JsValue]], fields:JsValue)
  implicit val searchResultRowFormat = jsonFormat3(SearchResultRow)
  case class SearchResponse(total_rows:Int, bookmark:String, rows:Seq[SearchResultRow])
  implicit val searchResponseFormat = jsonFormat3(SearchResponse)
  
  case class FacetedSearchResponse(
      rows:Seq[SearchResultRow],
      bookmark:String,
      total_rows:Int,
      ranges:Option[Map[String,Map[String,Int]]],
      counts:Option[Map[String,Map[String,Int]]])
  implicit val facetedSearchResponseFormat = jsonFormat5(FacetedSearchResponse)
  
  case class SearchResultGroup(by:String, total_rows:Int, rows:Seq[SearchResultRow])
  implicit val SearchResultGroupFormat = jsonFormat3(SearchResultGroup)
  case class GroupedSearchResponse(total_rows:Int, bookmark:Option[String], groups:Seq[SearchResultGroup])
  implicit val groupedSearchResponseFormat = jsonFormat3(GroupedSearchResponse)
  
  // _security document
  case class RolesAndNames(roles:Seq[String], names:Seq[String])
  implicit val RolesAndNamesFormat = jsonFormat2(RolesAndNames)
  case class SecuritySettings(readers:Option[RolesAndNames], writers:Option[RolesAndNames], admins:Option[RolesAndNames])
  implicit val SecuritySettingsFormat = jsonFormat3(SecuritySettings)
  
  //design docs
  case class DesignDoc(
      shows:Option[Map[String,String]]=None,
      views:Option[Map[String,MapReduce]]=None,
      lists:Option[Map[String,String]]=None
  )
  implicit val designDocFormat = jsonFormat3(DesignDoc)
  
  case class ViewQuery(startkey:Option[JsValue], endkey:Option[JsValue], limit:Option[Int], skip:Option[Int], include_docs:Option[Boolean])
  implicit val viewQueryFormat = jsonFormat5(ViewQuery)
  
  case class DbCopy[KEY, VALUE](key:KEY, value:VALUE, salt:JsValue, partials:JsValue) {
    def pair = key -> value
  }
  implicit def dbCopyFormat[KEY:JsonFormat, VALUE:JsonFormat] = jsonFormat4(DbCopy[KEY, VALUE])
  
  case class ShardsResponse(shards:Map[String, Seq[String]])
  implicit val shardsResponseFormat = jsonFormat1(ShardsResponse)
  case class ShardsDocIdResponse(nodes:Seq[String], range:String)
  implicit val shardsDocIdResponseFormat = jsonFormat2(ShardsDocIdResponse)
  
  case class MembershipResponse(cluster_nodes:Seq[String], all_nodes:Seq[String])
  implicit val membershipResponseFormat = jsonFormat2(MembershipResponse)
  
  case class SearchAnalyzeResult(tokens:Seq[String])
  implicit val searchAnalyzeResultFormat = jsonFormat1(SearchAnalyzeResult)
  
  case class SearchAnalyzeRequest(analyzer:String, text:String)
  implicit val searchAnalyzeRequestFormat = jsonFormat2(SearchAnalyzeRequest)
}


