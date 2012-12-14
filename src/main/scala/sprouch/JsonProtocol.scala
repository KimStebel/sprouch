package sprouch

import spray.can.client.HttpClient
import spray.client.HttpConduit
import HttpConduit._
import spray.http._
import HttpMethods._
import spray.httpx.encoding.{Gzip, Deflate}
import spray.httpx.SprayJsonSupport._
import spray.httpx.unmarshalling.Unmarshaller
import spray.io._
import spray.json._
import spray.util._
import java.util.UUID

trait Id {
  val id:String
}
trait Rev {
  val rev:String
}

object JsonProtocol extends JsonProtocol

trait JsonProtocol extends DefaultJsonProtocol {
  implicit val nullFormat:JsonFormat[Null] = new JsonFormat[Null] {
    override def read(js:JsValue) = if (js == JsNull) null else throw new Exception("null expected")
    override def write(n:Null) = JsNull
  }
  case class MapReduce(map:String, reduce:Option[String])
  implicit val mapReduceFormat = jsonFormat2(MapReduce)
  case class Views(views:Map[String,MapReduce]) 
  implicit val viewsFormat = jsonFormat1(Views)
  case class ViewRow[K,V](key:K, value:V)
  implicit def viewRowFormat[K:JsonFormat,V:JsonFormat] = jsonFormat2(ViewRow[K,V])
  case class ViewResponse[K,V](rows:List[ViewRow[K,V]])
  implicit def viewResponseFormat[K:JsonFormat,V:JsonFormat] = jsonFormat1(ViewResponse[K,V])
  case class AttachmentStub(stub:Boolean, content_type:String, length:Int)
  implicit val attachmentStubFormat = jsonFormat3(AttachmentStub)
  case class Revision(rev:String)
  implicit val revisionFormat = jsonFormat1(Revision)
  case class AllDocsRow[A](id:String, key:String, value:Revision, doc:A)
  implicit def allDocsRowFormat[A:RootJsonFormat] = jsonFormat4(AllDocsRow[A])
  case class AllDocsResponse[A](total_rows:Int, offset:Int, rows:Seq[AllDocsRow[A]])
  implicit def allDocsResponseFormat[A:RootJsonFormat] = jsonFormat3(AllDocsResponse[A])
  case class OkResponse(ok:Boolean)
  case class CreateResponse(ok:Boolean, id:String, rev:String)
  case class ErrorResponse(status:Int, body:ErrorResponseBody)
  case class ErrorResponseBody(error:String, reason:String)
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

  sealed trait Document[+A] extends Id {
    def data:A
    def revOpt:Option[String]
    def attachments:Map[String, AttachmentStub]
    def setRev(rev:String):RevedDocument[A] = new RevedDocument(id, rev, data, attachments)
    override def toString = "Document(id: " + id + revOpt.map(", rev: " +).getOrElse("") + ", data: " + data + ")" 
  }
  class RevedDocument[+A](
      val id:String,
      val rev:String,
      val data:A,
      val attachments:Map[String,AttachmentStub]
  ) extends Document[A] with Rev {
    def updateData[B](f:A=>B) = new RevedDocument(id, rev, f(data), attachments)
    def revOpt = Some(rev)
  }
  class NewDocument[+A](val id:String, val data:A, val attachments:Map[String,AttachmentStub]) 
  extends Document[A] {
    def this(id:String, data:A) = this(id, data, Map())
    def this(data:A) = this(UUID.randomUUID.toString, data, Map())
    def revOpt = None
  }
  
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
}


