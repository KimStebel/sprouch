package sprouch.synchronous

import scala.concurrent.duration.Duration
import sprouch.Config
import scala.concurrent.Future
import scala.concurrent.Await
import sprouch.JsonProtocol.OkResponse

/**
  * This is just a synchronous wrapper around sprouch.Couch.
  * Please look there for documentation. All the methods are identical,
  * except that they return A instead of Future[A].  
  */
class Couch private (c:sprouch.Couch, timeout:Duration) {
  
  private def await[A](f:Future[A]):A = Await.result(f, timeout)
  
  def createDb(dbName:String):Database = Database(await(c.createDb(dbName)), timeout)
  
  def deleteDb(dbName:String):OkResponse = await(c.deleteDb(dbName))
  
  def getDb(dbName:String):Database = Database(await(c.getDb(dbName)), timeout)

}

object Couch {
  
  def apply(config:Config, timeout:Duration = Duration("60 seconds")) = new Couch(sprouch.Couch(config), timeout)
  
}