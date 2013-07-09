package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import sprouch.dsl._
import spray.json.JsonWriter
import spray.json.JsObject
import spray.json.JsonReader

class Search extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  test("lucene based search") {
    implicit val dispatcher = (actorSystem.dispatcher)
        
    withNewDbFuture("search-sorting")(implicit dbf => {
      val data = List(Test(foo=0, bar="aa"),Test(11, "ab"),Test(2, "ac"), Test(3, "aa"), Test(22, "aa"), Test(3, "ba"), Test(4, "bb"))
      val index = Index("""
          function(doc){
            index("default", doc.bar, {"store": "yes"});
            index("foo", doc.foo, {"store": "yes"});
          }
      """)
      val indexes = Indexes(Map("bar" -> index))
      val ddocName = "mysearches"
      val indexesDoc = new NewDocument(ddocName, indexes)
      val dl = SphinxDocLogger("search")
      for {
        db <- dbf
        view <- db.createIndexes(indexesDoc)
        docs <- data.create
        queryRes1 <- db.search(ddocName, "bar", "a*", Some(Seq("foo<number>")), docLogger = dl)
        queryRes2 <- db.search(ddocName, "bar", "a*", Some(Seq("foo<number>")), docLogger = dl)
        
      } yield {
        val expectedDocs = docs.filter(_.data.bar.startsWith("a")).sortBy(_.data.foo)
        val expectedIds = expectedDocs.map(_.id)
        //assert(queryRes1.rows.map(_.id) === expectedIds) see https://cloudant.fogbugz.com/default.asp?20797
        assert(queryRes2.rows.map(_.id) === expectedIds)
      }
    })
  }
}
