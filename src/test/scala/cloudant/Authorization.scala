package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._

class Authorization extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  implicit val dispatcher = actorSystem.dispatcher
  
  /*test("generate api key") {
    val gakDl = SphinxDocLogger("../cloudant-api-reference/src/api/generateApiKey")
    await(for {
      _ <- ignoreFailure(c.createDb("db"))
      key <- c.withDl(gakDl) {
        c.generateApiKey
      }
    } yield {
    
    })
    
  }*/
    
}
