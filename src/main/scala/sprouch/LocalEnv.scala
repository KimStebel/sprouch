package sprouch

import akka.actor.ActorSystem
import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await

/**
 * Convenience methods and values for the default config of a local CouchDb install.
 * It should be used only for testing in the REPL. 
 */
object LocalEnv {
  import JsonProtocol._
  case class Test(foo:Int, bar:String)
  implicit val testFormat = jsonFormat2(Test)
  
  implicit val actorSystem = ActorSystem("MySystem")
  val couch = new Couch(Config(actorSystem, "localhost", 5984, None, false))
  var testDuration = Duration("10 seconds")
  def await[A](f:Future[A]) = Await.result(f, testDuration)
  
  implicit def extendFutureOfEither[A,B](f:Future[Either[A,B]]) = new {
    def r = await(f).right.get
    def l = await(f).left.get
  }
  
}