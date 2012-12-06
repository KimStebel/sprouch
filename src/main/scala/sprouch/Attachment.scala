package sprouch

import scala.Array.canBuildFrom

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