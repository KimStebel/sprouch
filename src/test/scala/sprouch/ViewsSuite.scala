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
        docs <- Future.sequence(data.map(d => db.createDoc(d).map(_.right.get)))
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
        val viewRes <- db.createViews(viewsDoc)
        val view = assertGet(viewRes)
        queryResE <- db.queryView[Null,Int]("my views", "sum")
        val queryRes = assertGet(queryResE)
        groupedResE <- db.queryView[String,Int]("my views", "sum", flags = ViewQueryFlag(group = true))
        val groupedRes = assertGet(groupedResE)
        keyResE <- db.queryView[Null,Int]("my views", "sum", key = Some("a"))
        val keyRes = assertGet(keyResE)
        keysResE <- db.queryView[String,Int]("my views", "sum", keys = List("a", "c"))
        val keysRes = assertGet(keysResE)
        rangeResE <- db.queryView[Null,Int]("my views", "sum", keyRange = Some("b" -> "c"))
        val rangeRes = assertGet(rangeResE)
        
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