package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._

class AuthenticationDoc extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  implicit val dispatcher = actorSystem.dispatcher
  
  def dl(name:String) = new SphinxDocLogger("../cloudant-api-reference/src/api/" + name)
  
  test("login") {
    await(for {
      cookie <- c.pipelines.cloudantLogin(docLogger = dl("Login"))
      _ <- c.pipelines.getAuthInfo(docLogger = dl("getAuthInfo"))
      _ <- c.pipelines.cloudantLogout(dl("Logout"))
    } yield {
      assert(cookie != "")
    })
    
  }  
    
}
