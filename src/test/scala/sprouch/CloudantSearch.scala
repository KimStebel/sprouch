package sprouch

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import akka.dispatch.Future
import spray.json.JsonFormat
import spray.json.JsValue

class CloudantSearch extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  test("lucene based search") {
    implicit val dispatcher = (actorSystem.dispatcher)
        
    withNewDb("db")(db => {
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
      val dl = SphinxDocLogger("../cloudant-api-reference/src/api/searchExample")
      for {
        view <- db.createIndexes(indexesDoc)
        docs <- Future.sequence(data.map(d => db.createDoc(d)))
        _ <- db.search("my searches", "foo", "0")
        queryRes <- db.search("my searches", "bar", "b*", docLogger = dl)
        _ = println(implicitly[JsonFormat[SearchResponse]].write(queryRes))
        
      } yield {
        val expectedIds = docs.filter(_.data.bar.startsWith("b")).map(_.id).toSet 
        assert(queryRes.rows.map(_.id).toSet === expectedIds)
      }
    })
  }
    
}
