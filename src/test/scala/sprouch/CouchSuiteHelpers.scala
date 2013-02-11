package sprouch

import org.scalatest.FunSuite
import akka.actor.ActorSystem
import akka.util.Duration
import akka.dispatch.Await
import akka.dispatch.Future
import java.util.UUID

case class Test(foo:Int, bar:String)

trait CouchSuiteHelpers {
  self:FunSuite =>
    
  import JsonProtocol._
  
  implicit val testFormat = jsonFormat2(Test)
  
  implicit val actorSystem = ActorSystem("MySystem")
  private val conf = Config(actorSystem, "kimstebel.cloudant.com", 80, Some("kimstebel" -> "lse72438"), false) 
  val c = new Couch(conf)
  val cSync = sprouch.synchronous.Couch(conf)
  implicit val testDuration = Duration("60 seconds")
  def await[A](f:Future[A]) = Await.result(f, testDuration)
  
  def assertGet[A](e:Either[_,A]):A = {
    assert(e.isRight, e)
    e.right.get
  }
  
  def withNewDb[A](f:Database => Future[A]):A = {
    val dbName = "tempdb" + UUID.randomUUID.toString.toLowerCase
    withNewDb(dbName)(f)
  }
  
  def withNewDb[A](name:String)(f:Database => Future[A]):A = {
    await(for {
      del <- c.deleteDb(name) recover { case _ => }
      db <- c.createDb(name)
      res <- f(db) andThen { case _ => db.delete() }
    } yield (res))
  }
  
  def withNewDbFuture[A](f:Future[Database] => Future[A]):A = {
    val dbName = "tempdb" + UUID.randomUUID.toString.toLowerCase
    val dbf = c.createDb(dbName)
    val resf = f(dbf)
    resf andThen { case _ => dbf.flatMap(_.delete()) }
    await(resf)
  }
  
  
  def withNewDbSync[A](f:sprouch.synchronous.Database => A):A = {
  	val dbName = "tempdb" + UUID.randomUUID.toString.toLowerCase
    val db = cSync.createDb(dbName)
    val res = f(db)
    db.delete
    res
  }
  
}