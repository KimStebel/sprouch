package sprouch

import org.scalatest.FunSuite
import akka.actor.ActorSystem
import java.util.UUID
import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await

case class Test(foo:Int, bar:String)

trait CouchSuiteHelpers {
  self:FunSuite =>
    
  import JsonProtocol._
  
  implicit val testFormat = jsonFormat2(Test)
  
  implicit val actorSystem = ActorSystem("MySystem")
  import actorSystem.dispatcher
  val c = new Couch(Config(actorSystem, "localhost", 5984, None, false))
  val testDuration = Duration("30 seconds")
  def await[A](f:Future[A]) = Await.result(f, testDuration)
  
  def assertGet[A](e:Either[_,A]):A = {
    assert(e.isRight, e)
    e.right.get
  }
  
  def withNewDb[A](f:Database => Future[A]):A = {
    val dbName = "tempdb" + UUID.randomUUID.toString.toLowerCase
    val res = c.createDb(dbName) flatMap {
      db => {
        f(db) andThen { case _ => db.delete() }
      }
    }
    await(res)
  }
  
}