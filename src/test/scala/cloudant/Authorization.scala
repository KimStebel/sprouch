package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import docLogger._

class Authorization extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  implicit val dispatcher = actorSystem.dispatcher
  
  test("get security document") {
    val dl = MdDocLogger("_securityGET")
    withNewDbFuture(implicit dbf => for {
      db <- dbf
      sec <- db.security(docLogger = dl)
    } yield {
      println(sec)
    })
  }
  
  
  /*test("generate api key") {
    val gakDl = MdDocLogger("../cloudant-api-reference/src/api/inc/generateApiKey")
    await(for {
      _ <- ignoreFailure(c.createDb("db"))
      key <- c.withDl(gakDl) {
        c.generateApiKey
      }
    } yield {
    
    })
    
  } */
    
}
