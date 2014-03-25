package cloudant

import org.scalatest.FunSuite
import sprouch._
import sprouch.dsl._

class ListFunctions extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  import actorSystem.dispatcher
  
  test("list functions") {
    val ddname = "mylists"
    withNewDbFuture(implicit dbf => {
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
      val dl = SphinxDocLogger("list")
      for {
        db <- dbf
        view <- db.createDesign(designDoc)
        docs <- data.create
        _ <- pause()
        queryRes <- db.list(ddname, "asHtml", "id", docLogger = dl)
      } yield {
        assert(queryRes.entity.asString === "<ul>" + (docs.map(_.data.foo).map("<li>"+_+"</li>").mkString) + "</ul>")
        assert(queryRes.headers.find(_.name.toLowerCase == "content-type").get.value === "text/html")
        assert(queryRes.status.value  === 200)
      }
    })
  }
    
}
