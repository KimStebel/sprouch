package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._

class Authorization extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  implicit val dispatcher = actorSystem.dispatcher
  
  test("get security document") {
    val dl = new SphinxDocLogger("../api-reference/src/api/inc/_securityGET")
    withNewDbFuture("db")(implicit dbf => for {
      db <- dbf
      sec <- c.withDl(dl) { db.security }
    } yield {
      println(sec)
    })
  }
  
  
  /*test("generate api key") {
    val gakDl = SphinxDocLogger("../cloudant-api-reference/src/api/inc/generateApiKey")
    await(for {
      _ <- ignoreFailure(c.createDb("db"))
      key <- c.withDl(gakDl) {
        c.generateApiKey
      }
    } yield {
    
    })
    
  } */
    
}
