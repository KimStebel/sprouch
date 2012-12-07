package sprouch

import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import akka.dispatch.Future

@RunWith(classOf[JUnitRunner])
class ViewsSuite extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  test("sum view") {
    implicit val dispatcher = (actorSystem.dispatcher)
        
    withNewDb(db => {
      val data = List(Test(foo=0, bar="a"),Test(1, "a"),Test(2, "b"), Test(3, "c"), Test(4, "c"))
      for {
        docs <- Future.sequence(data.map(d => db.createDoc(d)))
        val sum = docs.map(_.data.foo).sum
        val mr = MapReduce(
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
        val viewsDoc = new NewDocument("my views", Views(Map("sum" -> mr)))
        val view <- db.createViews(viewsDoc)
        queryRes <- db.queryView[Null,Int]("my views", "sum")
        groupedRes <- db.queryView[String,Int]("my views", "sum", flags = ViewQueryFlag(group = true))
        keyRes <- db.queryView[Null,Int]("my views", "sum", key = Some("a"))
        keysRes <- db.queryView[String,Int]("my views", "sum", keys = List("a", "c"))
        rangeRes <- db.queryView[Null,Int]("my views", "sum", keyRange = Some("b" -> "c"))
        
      } yield {
        assert(queryRes.rows.head.value === sum)
        val expectedGrouped = 
          data.groupBy(_.bar).map  { case (k,v) => ViewRow(k, v.map(_.foo).sum) }
        assert(groupedRes.rows.toSet === expectedGrouped.toSet)   
        assert(keyRes.rows === List(ViewRow(null, 1)))
        assert(keysRes.rows.toSet === Set(ViewRow("a", 1), ViewRow("c", 7)))
        assert(rangeRes.rows.head.value === 9)
      }
    })
  }
  
}