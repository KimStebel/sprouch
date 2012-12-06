package sprouch

sealed trait ViewQueryFlag
case object group extends ViewQueryFlag
case object descending extends ViewQueryFlag
case object reduce extends ViewQueryFlag
case object include_docs extends ViewQueryFlag
case object inclusive_end extends ViewQueryFlag
case object update_seq extends ViewQueryFlag

object ViewQueryFlag {
  val default:Set[ViewQueryFlag] = Set(reduce, inclusive_end)
  val all:Set[ViewQueryFlag] = Set(group, descending, reduce, include_docs, inclusive_end, update_seq)
  def apply(
      group:Boolean = false, 
      descending:Boolean = false, 
      reduce:Boolean = true, 
      include_docs:Boolean = false, 
      inclusive_end:Boolean = true, 
      update_seq:Boolean = false
      
  ):Set[ViewQueryFlag] = {
    Set(
       Option(sprouch.group).filter(_ => group),
       Option(sprouch.descending).filter(_ => descending),
       Option(sprouch.reduce).filter(_ => reduce),
       Option(sprouch.include_docs).filter(_ => include_docs),
       Option(sprouch.inclusive_end).filter(_ => inclusive_end),
       Option(sprouch.update_seq).filter(_ => update_seq)
       
    ).flatten
  }
}