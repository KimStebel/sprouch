package cloudant

import org.scalatest.FunSuite
import sprouch._

case class T4(price:Double, name:String, category:String, subcategory:String)

class SearchFaceting extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  import actorSystem.dispatcher

  test("search faceting") {

    implicit val t4format = jsonFormat4(T4)
    withNewDbFuture(implicit dbf => {
      val data = List(
          T4(9.95, "Hygiene and the Assasin", "books", "novels"),
          T4(300.00, "Nexus 5", "phones", "android phones"),
          T4(14.95, "Harry Potter and the Philosopher's Stone", "books", "novels"),
          T4(600.00, "Samsung S4", "phones", "android phones"), 
          T4(43.49, "Programming in Scala", "books", "programming books"),
          T4(32.50, "Programming Erlang", "books", "programming books"),
          T4(23.99, "Haskell Programming", "books", "programming books"))
      val index = Index("""
          function(doc) {
            index("name", doc.name, {"facet": true, "store": true});
            index("price", doc.price, {"facet": true, "store": true});
            index("category", doc.category, {"facet": true, "store": true});
            index("subcategory", doc.subcategory, {"facet": true, "store": true});
          }
      """, None)
      val indexName = "faceted"
      val indexes = Indexes(Map(indexName -> index))
      val ddocName = "doc"
      val indexesDoc = new NewDocument(ddocName, indexes)
      val dl = SphinxDocLogger("searchFaceting")
      for {
        db <- dbf
        view <- db.createIndexes(indexesDoc)
        docs <- db.bulkPut(data.map(d=>new NewDocument(d)))
        //_ <- db.search(ddocName, indexName, "bar:a*") //do a normal search first to work around bug
        //_ <- Future(Thread.sleep(5000)) //and wait to give it some time to initialize
        queryRes2 <- db.facetedSearch(
            ddocName,
            indexName,
            "name:H*",
            counts = Some(Seq("category")),
            ranges = Some(Map("price" -> Map("cheap" -> "[0 TO 10]", "okay" -> "[11 TO 500]", "expensive" -> "[501 TO 9999999]"))),
            drilldown = Some(Seq("subcategory" -> "novels")),
            docLogger = dl)
      } yield {
        val actualCategories = queryRes2.counts.get
        val expectedCategories = Map("category" -> Map("books" -> 2))
        val actualRanges = queryRes2.ranges.get
        val expectedRanges = Map("price" -> Map("cheap" -> 1, "okay" -> 1, "expensive" -> 0))
        assert(actualRanges === expectedRanges)
        assert(actualCategories === expectedCategories)
        
      }
    })
  }
}
