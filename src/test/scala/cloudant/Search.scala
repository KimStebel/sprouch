package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import sprouch.dsl._
import spray.json.JsonWriter

class Search extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  test("lucene based search") {
    implicit val dispatcher = (actorSystem.dispatcher)
        
    withNewDbFuture("db")(implicit dbf => {
      val data = List(Test(foo=0, bar="aa"),Test(1, "ab"),Test(2, "ac"), Test(3, "ba"), Test(4, "bb"))
      val index = Index("""
          function(doc){
            index("default", doc.bar, {"store": "yes"});
          index("foo", doc.foo, {"store": "yes"});
          }
      """)
      val fooIndex = Index("""
          function(doc) {
            index("foo", doc.foo, {"store": "yes"});
          }
      """)
      val indexes = Indexes(Map("bar" -> index, "foo" -> fooIndex))
      val indexesDoc = new NewDocument("my searches", indexes)
      val dl = new SphinxDocLogger("../api-reference/src/api/inc/search")
      for {
        db <- dbf
        view <- db.createIndexes(indexesDoc)
        docs <- data.create
        //_ <- db.search("my searches", "foo", "0")
        queryRes <- db.search("my searches", "bar", "a*", Some(Seq("foo<number>")), docLogger = dl)
        _ = println(implicitly[JsonFormat[SearchResponse]].write(queryRes))
        
      } yield {
        val expectedIds = docs.filter(_.data.bar.startsWith("a")).sortBy(_.data.foo).map(_.id)
        assert(queryRes.rows.map(_.id) === expectedIds)
      }
    })
  }
    
}
