package cloudant

import org.scalatest.FunSuite
import sprouch._

class Authorization extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  import actorSystem.dispatcher
  
  test("get security document") {
    val dl = SphinxDocLogger("_securityGET")
    withNewDbFuture(implicit dbf => for {
      db <- dbf
      sec <- db.security(docLogger = dl)
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
