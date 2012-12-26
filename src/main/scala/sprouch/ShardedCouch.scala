package sprouch

import akka.actor._
import akka.dispatch.Future
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
import akka.event.Logging
import java.net.URLEncoder.{encode => urlEncode}

import JsonProtocol._

class ShardedCouch private (actorSystem:ActorSystem, couches:List[Couch], sharder:Sharder)
    extends AbstractCouch[ShardedDatabase] {
  implicit val dispatcher = (actorSystem.dispatcher)
  private def ??? = throw new Exception("not yet implemented")
  
  private def createOrGetDb(f:Couch=>Future[Database]):Future[ShardedDatabase] = {
    for {
      dbs <- Future.sequence(couches.map(f))
    } yield {
      new ShardedDatabase(dbs, sharder)
    }
  }
  
  def createDb(dbName:String):Future[ShardedDatabase] = createOrGetDb(_.createDb(dbName))
  
  def getDb(dbName:String):Future[ShardedDatabase] = createOrGetDb(_.getDb(dbName))
  
  def deleteDb(dbName:String):Future[OkResponse] = {
    for {
      oks <- Future.sequence(couches.map(_.deleteDb(dbName)))
    } yield {
      OkResponse(oks.forall(_.ok))
    }
  }
  
}

object ShardedCouch {
  
  def apply(actorSystem:ActorSystem, couches:List[Couch], sharder:Sharder) = {
    new ShardedCouch(actorSystem, couches, sharder)
  }
  
  def serFun[F <: (Int=>Int) : Manifest](f:F) = {
    val m = implicitly[Manifest[F]]
    println("manifest: " + m.erasure.getName)
    println("concrete class:" + f.getClass.getName)
    println("interfaces: " + f.getClass.getInterfaces.mkString(", "))
  }
  
  def main(args:Array[String]) {
    object ff extends (Int=>Int) with Serializable {
      def apply(x:Int) = x + x
    }
    
    serFun((x:Int) => x + x)
  }
  
}