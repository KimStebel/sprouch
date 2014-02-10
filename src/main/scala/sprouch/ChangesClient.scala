package sprouch

import akka.dispatch.Future
import spray.json.JsonWriter
import spray.httpx.SprayJsonSupport._
import spray.httpx.RequestBuilding._
import akka.actor.Actor
import akka.actor.ActorRef
import akka.event.Logging
import spray.http.{ChunkedResponseStart, ChunkedMessageEnd, MessageChunk}
import akka.actor.Props
import spray.json._
import JsonProtocol._

trait ChangesJsonProtocol {
  self:DefaultJsonProtocol =>
    
  case class DbUpdate(dbname:String, `type`:String, seq:String, account:Option[String])
  implicit val dbUpdateFormat = jsonFormat4(DbUpdate)
  
  case class LastSeqResponse(last_seq:String)
  implicit val lastSeqResponseFormat = jsonFormat1(LastSeqResponse)
  
  case class RevObject(rev:String)
  implicit val revObjectFormat = jsonFormat1(RevObject)
  
  case class DocUpdate(seq:String, id:String, changes:Seq[RevObject])
  implicit val docUpdateFormat = jsonFormat3(DocUpdate)
  
  case class DocUpdates(results:Seq[DocUpdate], last_seq:String, pending:Int)
  implicit val docUpdatesFormat = jsonFormat3(DocUpdates)
  
  case class DbUpdates(results:Seq[DbUpdate], last_seq:String)
  implicit val dbUpdatesFormat = jsonFormat2(DbUpdates)  
  
}

trait DbChangesClient {
  val name:String
}

object ChangesModule {
  sealed trait Request
  case class LastSeq(wrapper:ActorRef => ActorRef = identity[ActorRef]) extends Request
  case class Continuous(since:Option[String] = None,
                        limit:Option[Int] = None,
                        descending:Option[Boolean] = None,
                        timeout:Option[Int] = None,
                        heartbeat:Option[Int] = None) extends Request with Params
  case class Longpoll(since:Option[String] = None,
                      limit:Option[Int] = None,
                      descending:Option[Boolean] = None,
                      timeout:Option[Int] = None,
                      heartbeat:Option[Int] = None) extends Request with Params
  trait Params {
    val since:Option[String]
    val limit:Option[Int]
    val descending:Option[Boolean]
    val timeout:Option[Int]
    val heartbeat:Option[Int]
  }
  case class ParseError(e:Exception)
  case object ResponseEnd
  case class LongpollResponse(docUpdates:DocUpdates)
    
  
}

import ChangesModule._

trait ChangesModule {
  trait ChangesActor {
    self: Actor =>
      
    protected[this] def handleRequest(actor: => Actor, request:Request) {
      val requestActor = context.actorOf(Props(actor))
      requestActor forward request
    }
    
  }
  
  protected[this] val pipelines:Pipelines
  
  protected[this] def paramsToQuery(p:Params) = {
    val params = p.since.map("since=" +) :: 
                 p.limit.map("limit=" +) :: 
                 p.descending.map("descending=" +) :: 
                 p.timeout.map("timeout=" +) :: 
                 p.heartbeat.map("heartbeat=" +) :: Nil
    params.flatten.mkString("&")
  }
  
  trait LastSeqRequestActor extends Actor {
    val uri:String 
    var reqSender:ActorRef = null
    var acc:String = ""
    val log = Logging(context.system, this)
    def receive = {
      case ls@LastSeq(_) => {
        reqSender = sender
        val cl = pipelines.conduit
        val req = pipelines.addBasicAuth(Get(uri))
        cl ! req
      }
      case crs:ChunkedResponseStart => {
        
      }
      case ChunkedMessageEnd => {
        try {
          val ls = acc.asJson.convertTo[LastSeqResponse]
          reqSender ! ls
        } catch {
          case e => {
            log.error("not an updates response: " + e)
            reqSender ! ParseError
          }
        }
        context.stop(self)
      }
      case mc:MessageChunk => {
        val str = mc.data.asString
        acc = acc + str
      }
    }
  }
  
  trait ContinuousRequestActor[A] extends Actor {
    val description:String
    val baseUri:String
    implicit val aJsonFormat:JsonFormat[A]
    var reqSender:ActorRef = null
    val log = Logging(context.system, this)
    val logger = context.actorFor("akka://MySystem/user/docLogger")
    def receive = {
      case p:Params => {
        reqSender = sender
        val cl = pipelines.conduit
        val query = paramsToQuery(p)
        val req = pipelines.addBasicAuth(Get(baseUri + query))
        logger ! ChunkedResponseLoggerActor.RequestHeaders(description, req)
        cl ! req
      }
      case crs:ChunkedResponseStart => {
        logger ! ChunkedResponseLoggerActor.ResponseHeaders(description, crs.message)
      }
      case ChunkedMessageEnd => {
        reqSender ! ResponseEnd
        context.stop(self)
      }
      case mc:MessageChunk => {
        try {
          val str = mc.data.asString
          val js = str.asJson
          val update = js.convertTo[A]
          logger ! ChunkedResponseLoggerActor.ResponseBodyJson(description, js)
          reqSender ! update
        } catch {
          case e => {
            //not an update chunk ->ignore
          }
        }
      }
    }
  }
  
  trait LongpollRequestActor[A] extends Actor {
    val description:String
    val baseUri:String
    implicit val aJsonFormat:JsonFormat[A]
    var reqSender:ActorRef = null
    var acc:String = ""
    val log = Logging(context.system, this)
    val logger = context.actorFor("akka://MySystem/user/docLogger")
    def receive = {
      case p:Params => {
        reqSender = sender
        val cl = pipelines.conduit
        val query = paramsToQuery(p)
        val req = pipelines.addBasicAuth(Get(baseUri + query))
        logger ! ChunkedResponseLoggerActor.RequestHeaders(description, req)
        cl ! req
      }
      case crs:ChunkedResponseStart => {
        logger ! ChunkedResponseLoggerActor.ResponseHeaders(description, crs.message)
      }
      case ChunkedMessageEnd => {
        try {
          val js = acc.asJson
          logger ! ChunkedResponseLoggerActor.ResponseBodyJson(description, js)
          val updates = js.convertTo[A]
          reqSender ! updates
        } catch {
          case e => {
            log.error("not an updates response: " + e)
            reqSender ! ParseError
          }
        }
        context.stop(self)
      }
      case mc:MessageChunk => {
        val str = mc.data.asString
        acc = acc + str
      }
    }
  }
}

trait DbChangesModule extends ChangesModule {
  val name:String
  protected[this] val config:Config
    
  import pipelines._
  
  object DbChangesActor {
    val actorName = "dbChangesActor" + name
    val changesActorRef = config.actorSystem.actorOf(Props(new DbChangesActor), actorName)
  }
  
  class DbChangesActor extends Actor with ChangesActor {
    import DbChangesActor._
    val log = Logging(context.system, DbChangesActor.this)
    
    override def receive = {
      case ls@LastSeq(_) => {
        handleRequest(new DbLastSeqRequestActor, ls)
      }
      case r:Continuous => {
        handleRequest(new DbContinuousRequestActor, r)
      }
      case r:Longpoll => {
        handleRequest(new DbLongpollRequestActor, r)
      }
      case other => {
        log.error("unexpected message: " + other)
        context.stop(self)
      }
    }
  }
  
  private class DbLongpollRequestActor extends LongpollRequestActor[DocUpdates] {
    override val description = "DbLongpollChangesFeed"
    override val baseUri = "/" + name  + "/_changes?feed=longpoll&"
    override val aJsonFormat = implicitly[JsonFormat[DocUpdates]]
  }
  
  private class DbLastSeqRequestActor extends LastSeqRequestActor {
    override val uri = "/" + name + "/_changes?limit=1&descending=true"
  }
  
  protected[this] class DbContinuousRequestActor extends ContinuousRequestActor[DocUpdate] {
    val description = "DbContinuousChangesFeed"
    import DbChangesActor._
    override val baseUri = "/" + name + "/_changes?feed=continuous&"
    override val aJsonFormat = implicitly[JsonFormat[DocUpdate]]
    
  }
  
}

trait GlobalChangesModule extends ChangesModule {
  protected[this] val config:Config
    
  import pipelines._
  
  object GlobalChangesActor {
    val actorName = "globalChangesActor"
    val changesActorRef = config.actorSystem.actorOf(Props(new GlobalChangesActor), actorName)
    case class LongpollResponse(dbUpdates:DbUpdates)
  }
  
  private class GlobalChangesActor extends Actor with ChangesActor {
    import GlobalChangesActor._
    val log = Logging(context.system, GlobalChangesActor.this)
    
    override def receive = {
      case ls@LastSeq(_) => {
        handleRequest(new GlobalLastSeqRequestActor, ls)
      }
      case c:Continuous => {
        handleRequest(new GlobalContinuousRequestActor, c)
      }
      case l:Longpoll => {
        handleRequest(new GlobalLongpollRequestActor, l)
      }
      case other => {
        log.error("unexpected message: " + other)
        context.stop(self)
      }
    }
  }
  
  private class GlobalLongpollRequestActor extends LongpollRequestActor[DbUpdates] {
    override val description = "GlobalLongpollChangesFeed"
    override val baseUri = "/_db_updates?feed=longpoll&"
    override val aJsonFormat = implicitly[JsonFormat[DbUpdates]]
  }
  
  private class GlobalLastSeqRequestActor extends LastSeqRequestActor {
    override val uri = "/_db_updates?limit=1&descending=true"
  }
  
  private class GlobalContinuousRequestActor extends ContinuousRequestActor[DbUpdate] {
    val description = "GlobalContinuousChangesFeed"
    import GlobalChangesActor._
    override val baseUri = "/_db_updates?feed=continuous&"
    override val aJsonFormat = implicitly[JsonFormat[DbUpdate]]
    
  }  
}