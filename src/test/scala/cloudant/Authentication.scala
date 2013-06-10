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
      cookie <- c.withDl(dl("Login")) {
        c.pipelines.cloudantLogin()
      }
      _ <- c.withDl(dl("getAuthInfo")) {
        c.pipelines.getAuthInfo
      }
      _ <- c.withDl(dl("Logout")) {
        c.pipelines.cloudantLogout()
      }
    } yield {
      assert(cookie != "")
    })
    
  }  
    
}
