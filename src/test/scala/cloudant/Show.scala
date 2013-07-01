package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import sprouch.dsl._
import spray.json.JsonWriter

class Show extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  /*test("list functions") {
    implicit val dispatcher = actorSystem.dispatcher
        
    withNewDbFuture("db")(implicit dbf => {
      val data = Seq(Test(foo=2, bar="baz"),Test(foo=1, bar="foo"))
      val views = Map("foo"->MapReduce("function(doc){emit (doc.id, doc.foo);}"))
      val designDocContent = DesignDoc(views = Some(views), lists = Some(Map("asHtml" -> """
          function(head, req) {
            start({
              "headers": {
                "Content-Type": "text/html"
              }
            });
            send('<h1>' + req.query.h + '</h1><ul>');
            while(row = getRow()) {
              send('<li>' + row.value + '</li>');
            }
            send('</ul>');
          }
      """)))
      val designDoc = new NewDocument("my lists", designDocContent)
      val dl = new SphinxDocLogger("../api-reference/src/api/inc/list")
      for {
        db <- dbf
        view <- db.createDesign(designDoc)
        doc <- data.create
        queryRes <- c.withDl(dl) {
          db.list("my lists", "asHtml", "myview")
        }
      } yield {
        assert(queryRes.entity.asString === "<h1>heading</h1><ul><li>1</li><li>foo</li></ul>")
        assert(queryRes.headers.find(_.name.toLowerCase == "content-type").get.value === "text/html")
        
      }
    })

  }*/
  
  test("show functions") {
    implicit val dispatcher = actorSystem.dispatcher
        
    withNewDbFuture("db")(implicit dbf => {
      val data = Test(foo=1, bar="foo")
      val designDocContent = DesignDoc(lists = None, shows = Some(Map("asHtml" -> """
          function(doc, req) {
            return {
              body: ('<h1>' + req.query.h + '</h1><ul><li>' + doc.foo + '</li><li>' + doc.bar + '</li></ul>'),
              headers: { 'Content-Type': 'text/html' }
      			};
          }
      """)))
      val designDoc = new NewDocument("my shows", designDocContent)
      val dl = new SphinxDocLogger("../api-reference/src/api/inc/show")
      for {
        db <- dbf
        view <- db.createDesign(designDoc)
        doc <- data.create
        val query = "h=heading"
        queryRes <- db.show("my shows", "asHtml", doc.id, query, docLogger = dl)
      } yield {
        assert(queryRes.entity.asString === "<h1>heading</h1><ul><li>1</li><li>foo</li></ul>")
        assert(queryRes.headers.find(_.name.toLowerCase == "content-type").get.value === "text/html")
        
      }
    })
  }
}
