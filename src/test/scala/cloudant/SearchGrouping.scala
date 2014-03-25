package cloudant

import org.scalatest.FunSuite
import sprouch._
import sprouch.dsl._
import scala.concurrent.Future

class SearchGrouping extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  import actorSystem.dispatcher

  case class Thing(foo:String, bar:String)
  implicit val thingFormat = jsonFormat2(Thing)
  
  test("lucene based search - grouping") {
        
    withNewDbFuture(implicit dbf => {
      val data = Seq(
          Thing(foo="group0", bar="aa"),
          Thing("group0", "ab"),
          Thing("group0", "ac"),
          Thing("group1", "ad"),
          Thing("group1", "ae"), 
          Thing("group1", "ba"),
          Thing("group2", "bb"))
      val index = Index("""
          function(doc){
            index("default", doc.bar, {"store": "yes"});
            index("bar", doc.bar, {"store": "yes"});
            index("foo", doc.foo, {"store": "yes"});
          }
      """, None)
      val indexName = "index1"
      val indexes = Indexes(Map(indexName -> index))
      val ddocName = "mysearches"
      val indexesDoc = new NewDocument(ddocName, indexes)
      val dl = SphinxDocLogger("searchGrouping")
      for {
        db <- dbf
        view <- db.createIndexes(indexesDoc)
        docs <- data.create
        _ <- Future(Thread.sleep(3000))
        queryRes1 <- db.groupedSearch(ddocName, indexName, "a*", sort = None, groupField = "foo", docLogger = dl)
        queryRes2 <- db.groupedSearch(ddocName, indexName, "b*", sort = None, groupField = "foo")
        queryRes3 <- db.groupedSearch(ddocName, indexName, "a*", sort = Some(Seq("bar<string>")), groupField = "foo", groupSort = Some(Seq("-foo<string>")))
        queryRes4 <- db.groupedSearch(ddocName, indexName, "a*", sort = Some(Seq("bar<string>")), groupField = "foo", groupSort = Some(Seq("-foo<string>")))
        
      } yield {
        val expectedIds1 = docs.filter(_.data.bar.startsWith("a")).groupBy(_.data.foo).map({case (k,v) => k -> v.map(_.id).toSet}).toMap
        val expectedIds2 = docs.filter(_.data.bar.startsWith("b")).groupBy(_.data.foo).map({case (k,v) => k -> v.map(_.id).toSet}).toMap
        val expectedIds3 = docs.filter(_.data.bar.startsWith("a")).groupBy(_.data.foo).map({case (k,v) => k -> v.sortBy(_.data.bar).map(_.id)}).toSeq.sortWith((x,y) => x._1 > y._1).toMap
        assert(queryRes1.groups.map(g => g.by -> g.rows.map(_.id).toSet).toMap === expectedIds1)
        assert(queryRes2.groups.map(g => g.by -> g.rows.map(_.id).toSet).toMap === expectedIds2)
        def toMap(r:GroupedSearchResponse) = {
          r.groups.map(g => g.by -> g.rows.map(_.id)).toMap
        }
        assert(toMap(queryRes3) === expectedIds3)
        assert(toMap(queryRes3) === toMap(queryRes4))
      }
    })
  }
}
