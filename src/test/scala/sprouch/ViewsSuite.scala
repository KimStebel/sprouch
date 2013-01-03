package sprouch

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import scala.concurrent.Future
import spray.json.JsonFormat
import spray.json.JsValue

class ViewsSuite extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  test("sum view") {
    implicit val dispatcher = (actorSystem.dispatcher)
        
    withNewDb(db => {
      val data = List(Test(foo=0, bar="a"),Test(1, "a"),Test(2, "b"), Test(3, "c"), Test(4, "c"))
      val dataFutures = data.map(d => db.createDoc(d))
      for {
        docs <- Future.sequence(dataFutures)
        sum = docs.map(_.data.foo).sum
        mr = MapReduce(
            map = """
              function(doc) {
                emit(doc.bar, doc.foo);
              }
            """,
            reduce = Some("""
              function(keys, values) {
                return sum(values);
              }
            """)
        )
        viewsDoc = new NewDocument("my views", Views(Map("sum" -> mr)))
        view <- db.createViews(viewsDoc)
        queryRes <- db.queryView[Null,Int]("my views", "sum")
        groupedRes <- db.queryView[String,Int]("my views", "sum", flags = ViewQueryFlag(group = true))
        keyRes <- db.queryView[Null,Int]("my views", "sum", key = Some("a"))
        keysRes <- db.queryView[String,Int]("my views", "sum", keys = List("a", "c"))
        rangeRes <- db.queryView[Null,Int]("my views", "sum", startKey = Some("b"), endKey = Some("c"))
        groupedRes2 <- db.queryView[String,Int]("my views", "sum", groupLevel = Some(1))
      } yield {
        assert(queryRes.rows.head.value === sum)
        val expectedGrouped = data.groupBy(_.bar).map { case (k,v) => ViewRow(k, v.map(_.foo).sum, None) }
        assert(groupedRes.rows.toSet === expectedGrouped.toSet)   
        assert(keyRes.rows === List(ViewRow(null, 1, None)))
        assert(keysRes.rows.toSet === Set(ViewRow("a", 1, None), ViewRow("c", 7, None)))
        assert(rangeRes.rows.head.value === 9)
        assert(groupedRes === groupedRes2)
      }
    })
  }
  test("map view") {
    implicit val dispatcher = (actorSystem.dispatcher)
        
    withNewDb(db => {
      val data = List(Test(foo=0, bar="a"),Test(1, "a"),Test(2, "b"), Test(3, "c"), Test(4, "c"))
      val dataFutures = data.map(d => db.createDoc(d))
      for {
        docs <- Future.sequence(dataFutures)
        sum = docs.map(_.data.foo).sum
        mr = MapReduce(
            map = """
              function(doc) {
                emit(doc.bar, doc.foo);
              }
            """,
            reduce = None
        )
        viewsDoc = new NewDocument("my views", Views(Map("map" -> mr)))
        view <- db.createViews(viewsDoc)
        queryRes <- db.queryView[String,Int]("my views", "map",
            flags = ViewQueryFlag(descending = true, include_docs = false, reduce = false, group=false),
            limit = Some(10))
        withDocs <- db.queryView[String,Int]("my views", "map",
            flags = ViewQueryFlag(descending = true, include_docs = true, reduce = false, group=false),
            limit = Some(10))
        
      } yield {
        assert(queryRes.values.toSet === data.map(_.foo).toSet)
        val docsFromQuery = withDocs.docs[Test].toSet
        assert(docsFromQuery === data.toSet)
      }
    })
  }
  test("map view with complex keys") {
    implicit val dispatcher = (actorSystem.dispatcher)
        
    withNewDb(db => {
      val data = List(Test(foo=0, bar="a"),Test(1, "a"),Test(2, "b"), Test(3, "c"), Test(4, "c"))
      val dataFutures = data.map(d => db.createDoc(d))
      for {
        docs <- Future.sequence(dataFutures)
        sum = docs.map(_.data.foo).sum
        mr = MapReduce(
            map = """
              function(doc) {
                emit([doc.bar,doc.foo], doc.foo);
              }
            """,
            reduce = None
        )
        viewsDoc = new NewDocument("my views", Views(Map("map" -> mr)))
        view <- db.createViews(viewsDoc)
        queryRes <- db.queryView[(String,Int),Int]("my views", "map",
            flags = ViewQueryFlag(reduce = false, group=false), startKey = Some(("a" -> 0)), endKey = Some("c" -> 4))
      } yield {
        assert(queryRes.keys.toSet === data.map(d => d.bar -> d.foo).toSet)
        assert(queryRes.values.toSet === data.map(_.foo).toSet)
        
      }
    })
  }
  
}
