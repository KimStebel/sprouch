package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import sprouch.dsl._
import spray.json.JsonWriter
import spray.json.JsArray

class ViewQueries extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  test("view queries") {
    implicit val dispatcher = (actorSystem.dispatcher)
    val ddname = "queries"
    withNewDbFuture("db")(implicit dbf => {
      val data = Seq(Test(foo=0, bar="foo"),Test(foo=1, bar="bar"),Test(foo=2, bar="baz"))
      val designDocContent = DesignDoc(views=Some(Map("id" -> MapReduce(map="""
        function(doc) {
          emit(doc.foo, doc);
        }
      """))))
      val designDoc = new NewDocument(ddname, designDocContent)
      val dl = new SphinxDocLogger("../api-reference/src/api/inc/viewQueries")
      val queries = Seq(
          ViewQuery(None, None, None, None, None),
          ViewQuery(Some(1), None, Some(2), None, None)
      )
      for {
        db <- dbf
        view <- db.createDesign(designDoc)
        docs <- data.create
        queryRes <- db.viewQueries(ddname, "id", queries, docLogger = dl)
      } yield {
        assert(queryRes.size === 2)
        assert(queryRes(0).fields.toMap.apply("rows").asInstanceOf[JsArray].elements.size === 3)
        assert(queryRes(1).fields.toMap.apply("rows").asInstanceOf[JsArray].elements.size === 2)
      }
    })
  }
}
