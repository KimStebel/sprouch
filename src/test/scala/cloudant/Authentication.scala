package cloudant

import org.scalatest.FunSuite
import sprouch._

/* TODO
class Authentication extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  implicit val dispatcher = actorSystem.dispatcher
  
  def dl(name:String) = SphinxDocLogger(name)
  
  test("login") {
    await(for {
      cookie <- c.pipelines.cloudantLogin(docLogger = dl("Login"))
      _ <- pause()
      _ <- c.pipelines.getAuthInfo(docLogger = dl("getAuthInfo"))
      _ <- pause()
      _ <- c.pipelines.cloudantLogout(dl("Logout"))
    } yield {
      assert(cookie != "")
    }) 
  }    
}
 */