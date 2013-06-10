package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import sprouch.dsl._
import spray.json.JsonWriter

class DeleteCreateDB extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  test("delete and create db") {
    implicit val dispatcher = (actorSystem.dispatcher)
    for (i <- 1 to 20) {
	    await(for {
	      _ <- (c.deleteDb("foo")) recover { case _ => }
	      db <- c.createDb("foo")
	      _ <- db.bulkPut((1 to 100).map(i => NewDocument(Test(1,"1"))))
	      _ <- db.delete
	      _ <- c.createDb("foo")
	      
	    } yield {})
    }
    
  }
}