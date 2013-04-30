package sprouch

import JsonProtocol._
import java.util.UUID

/**
 * This trait represents documents in CouchDB. Each document has an ID and data.
 *  The type of the data has to be convertable to a JSON object.
 *  Thus, there has to be an implicit RootJsonFormat[A] available.
 *  The document trait is sealed and has two subtypes: NewDocument and RevedDocument.
 *  The only difference is, that RevedDocument contains a rev field, the revision of the document, while NewDocument does not.
 *  NewDocument is only used for documents that have not been stored to CouchDB yet.
 *  Often, you don't need to use this type directly. All methods that return documents stored in CouchDb return instances of
 *  RevedDocument.
 *
 *  @tparam A The type of the data contained in the document.
 */
sealed trait Document[+A] extends Id {
  def data:A
  def revOpt:Option[String]
  /**
   * A map from attachment ids to attachment metadata.
   */
  def attachments:Map[String, AttachmentStub]
  private[sprouch] def setRev(rev:String):RevedDocument[A] = new RevedDocument(id, rev, data, attachments)
  override def toString = "Document(id: " + id + revOpt.map(", rev: " +).getOrElse("") + ", data: " + data + ")"
  override def equals(other:Any) = other match {
    case r:RevedDocument[_] => r.id == id && r.revOpt == revOpt && r.data == data && r.attachments == attachments
    case _ => false
  }
}
/**
 * @see sprouch.Document[A]
 */
class RevedDocument[+A](
    val id:String,
    val rev:String,
    val data:A,
    val attachments:Map[String,AttachmentStub]
) extends Document[A] with Rev {
  /**
   * Creates a new Document[B] by applying f to the data field.
   */
  def updateData[B](f:A=>B) = new RevedDocument(id, rev, f(data), attachments)
  def revOpt = Some(rev)
}
object RevedDocument {
  def apply[A](id:String, rev:String, data:A, attachments:Map[String,AttachmentStub] = Map()) =
    new RevedDocument(id, rev, data, attachments)
}

/**
 * @see sprouch.Document[A]
 */
class NewDocument[+A](val id:String, val data:A, val attachments:Map[String,AttachmentStub]) extends Document[A] {
  def this(id:String, data:A) = this(id, data, Map())
  def this(data:A) = this(UUID.randomUUID.toString, data, Map())
  def revOpt = None
}
object NewDocument {
  def apply[A](id:String, data:A, attachments:Map[String,AttachmentStub]):NewDocument[A] =
    new NewDocument(id, data, attachments)
  def apply[A](id:String, data:A):NewDocument[A] = apply(id, data, Map())
  def apply[A](data:A):NewDocument[A] = apply(UUID.randomUUID.toString, data, Map())
}
  
/**
 * Class that holds the javascript functions for a view.
 */
case class MapReduce(map:String, reduce:Option[String])
object MapReduce {
  def apply(map:String):MapReduce = MapReduce(map, None)
  def apply(map:String, reduce:String):MapReduce = MapReduce(map, Some(reduce))
}
/**
 * Class that holds the views of a view document.
 */
case class Views(views:Map[String,MapReduce], language:String = "javascript")
