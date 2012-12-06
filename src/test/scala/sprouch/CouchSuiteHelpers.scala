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
  val c = new Couch(Config(actorSystem, "localhost", 5984, None, false))
  val testDuration = Duration("10 seconds")
  def await[A](f:Future[A]) = Await.result(f, testDuration)
  
  def assertGet[A](e:Either[_,A]):A = {
    assert(e.isRight, e)
    e.right.get
  }
  
  def withNewDb[A](f:Database => Future[A]):A = {
    val dbName = "tempdb" + UUID.randomUUID.toString.toLowerCase
    val res = c.createDb(dbName) flatMap { 
      case Right(db) => {
        f(db) andThen { case _ => db.delete() }
      }
      case x => fail("failed to create db with name" + dbName + ": " + x)
    }
    await(res)
  }
  
}