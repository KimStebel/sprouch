package sprouch

sealed trait ViewQueryFlag

/**
 * Boolean flags for querying views and _all_docs. See the CouchDB documentation for the meaning of these flags.
 */
object ViewQueryFlag {
  case object group extends ViewQueryFlag
  case object descending extends ViewQueryFlag
  case object reduce extends ViewQueryFlag
  case object include_docs extends ViewQueryFlag
  case object inclusive_end extends ViewQueryFlag
  case object update_seq extends ViewQueryFlag
  
  /**
   * Set of default flags that are true by default according to CouchDB documentation.
   */
  val default:Set[ViewQueryFlag] = Set(reduce, inclusive_end)
  /**
   * Set of all flags
   */
  val all:Set[ViewQueryFlag] = Set(group, descending, reduce, include_docs, inclusive_end, update_seq)
  /**
   * Create a Set of flags with default values as given in the CouchDB documentation.
   */
  def apply(
      group:Boolean = false, 
      descending:Boolean = false, 
      reduce:Boolean = true, 
      include_docs:Boolean = false, 
      inclusive_end:Boolean = true, 
      update_seq:Boolean = false
      
  ):Set[ViewQueryFlag] = {
    Set(
       Option(ViewQueryFlag.group).filter(_ => group),
       Option(ViewQueryFlag.descending).filter(_ => descending),
       Option(ViewQueryFlag.reduce).filter(_ => reduce),
       Option(ViewQueryFlag.include_docs).filter(_ => include_docs),
       Option(ViewQueryFlag.inclusive_end).filter(_ => inclusive_end),
       Option(ViewQueryFlag.update_seq).filter(_ => update_seq)
       
    ).flatten
  }
}