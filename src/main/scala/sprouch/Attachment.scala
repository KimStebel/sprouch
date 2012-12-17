package sprouch

/**
  * case class holding the id and data of an attachment
  */
case class Attachment(id:String, data:Array[Byte]) {
  override def equals(a:Any) = a match {
    case a:Attachment => {
      a.id == id &&
      a.data.size == data.size &&
      a.data.view.zip(data).forall { case (x,y) => x == y }
    }
    case _ => false
  }
}

/**
 * Contains metadata about the attachment, but not the attachment itself.
 */
case class AttachmentStub(stub:Boolean, content_type:String, length:Int)
