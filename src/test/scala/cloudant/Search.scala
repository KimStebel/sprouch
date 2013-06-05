package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import sprouch.dsl._

class Search extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  test("lucene based search") {
    implicit val dispatcher = (actorSystem.dispatcher)
        
    withNewDbFuture("db")(implicit dbf => {
      val data = List(Test(foo=0, bar="abc"),Test(1, "bcd"),Test(2, "cde"), Test(3, "def"), Test(4, "bbb"))
      val index = Index("""
          function(doc){
            index("default", doc.bar, {"store": "yes"});
          }
      """)
      val fooIndex = Index("""
          function(doc) {
            index("foo", doc.foo, {"store": "yes"});
          }
      """)
      val indexesDoc = new NewDocument("my searches", Indexes(Map("bar" -> index, "foo" -> fooIndex)))
      val dl = SphinxDocLogger("../cloudant-api-reference/src/api/search")
      for {
        db <- dbf
        view <- db.createIndexes(indexesDoc)
        docs <- data.create
        _ <- db.search("my searches", "foo", "0")
        queryRes <- c.withDl(dl) { db.search("my searches", "bar", "b*", Some(Seq("foo"))) }
        _ = println(implicitly[JsonFormat[SearchResponse]].write(queryRes))
        
      } yield {
        val expectedIds = docs.filter(_.data.bar.startsWith("b")).map(_.id).toSet 
        assert(queryRes.rows.map(_.id).toSet === expectedIds)
      }
    })
  }
    
}
