package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import sprouch.dsl._
import spray.json.JsonWriter

class List extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  test("list functions") {
    implicit val dispatcher = (actorSystem.dispatcher)
    val ddname = "mylists"
    withNewDbFuture("db")(implicit dbf => {
      val data = Seq(Test(foo=0, bar="foo"),Test(foo=1, bar="bar"),Test(foo=2, bar="baz"))
      val designDocContent = DesignDoc(lists = Some(Map("asHtml" -> """
        function(head, req) {
          start({code: 200, headers: {"content-type": "text/html"}});
          var row;
          send("<ul>");
          while (row = getRow()) {
      			send("<li>" + row.value.foo + "</li>");
      		}
          send("</ul>");
         
        }
      """)), views=Some(Map("id" -> MapReduce(map="""
        function(doc) {
          emit(doc.foo, doc);
        }
      """))))
      val designDoc = new NewDocument(ddname, designDocContent)
      val dl = new SphinxDocLogger("../api-reference/src/api/inc/list")
      for {
        db <- dbf
        view <- db.createDesign(designDoc)
        docs <- data.create
        queryRes <- c.withDl(dl) {
          db.list(ddname, "asHtml", "id")
        }
      } yield {
        assert(queryRes.entity.asString === "<ul>" + (docs.map(_.data.foo).map("<li>"+_+"</li>").mkString) + "</ul>")
        assert(queryRes.headers.find(_.name.toLowerCase == "content-type").get.value === "text/html")
        assert(queryRes.status.value  === 200)
      }
    })
  }
    
}
