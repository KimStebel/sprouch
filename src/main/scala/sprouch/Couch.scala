package sprouch

import akka.actor._
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
import scala.concurrent.Future

trait UriBuilder {
  protected[this] def sep = "/"
  protected[this] def encode(s:String) = urlEncode(s, "UTF-8") 
  protected[this] def path(parts:String*) = sep + parts.map(encode).mkString(sep)  
  protected[this] def dbUri(dbName:String) = path(dbName)
}

case class SprouchException(error:ErrorResponse) extends Exception

class Couch(config:Config) extends UriBuilder {
  private val as = config.actorSystem
  import as.dispatcher
	
  private val myPipelines = new Pipelines(config)
  private lazy val pipeline = myPipelines.pipeline[OkResponse]
  private lazy val getDbPipeline = myPipelines.pipeline[GetDbResponse]
  
  def createDb(dbName:String):Future[Database] = {
    pipeline(Put(dbUri(dbName))).map(_ => new Database(dbName, myPipelines))
  }
  def deleteDb(dbName:String):Future[OkResponse] = {
    pipeline(Delete(dbUri(dbName)))
  }
  def getDb(dbName:String):Future[Database] = {
    getDbPipeline(Get(dbUri(dbName))).map(_ => new Database(dbName, myPipelines))
  }

}
