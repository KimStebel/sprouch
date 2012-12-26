package sprouch

trait Sharder extends (String => Int) with ((String,String) => Set[Int]) {
  def apply(id:String):Int
  def apply(fromId:String, toId:String):Set[Int]
}

class IntPrefixSharder(shards:Int, prefixLength:Int) extends Sharder {
  private def prefix(id:String) = id.substring(0,prefixLength)
  override def apply(id:String) = {
    prefix(id).hashCode % shards
  }
  override def apply(fromId:String, toId:String) = {
    val fromPrefix = BigInt(prefix(fromId))
    val toPrefix = BigInt(prefix(toId))
    fromPrefix.to(toPrefix).map(p => apply(p.toString)).toSet
  }
}