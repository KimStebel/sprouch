package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import sprouch.dsl._
import spray.json.JsonWriter
import spray.json.JsArray

class DbCopy extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol.{DbCopy => DbCopyContent, _}
  
  test("dbcopy") {
    implicit val dispatcher = (actorSystem.dispatcher)
    val ddName = "dbcopy"
    case class Employee(name:String, company:String)
    implicit val employeeFormat = jsonFormat2(Employee)
    withNewDbFuture(implicit dbf => {
      val data = Seq(
          "Adam" -> "Amazon",
          "Andy" -> "Amazon",
          "Anna" -> "Amazon",
          "Rudy" -> "Rackspace",
          "Richard" -> "Rackspace",
          "Clyde" -> "Cloudant").map{case (name, company) => Employee(name, company)}
      val viewName = "employeesByCompany"
      val dbCopyDb = randomDbName
      val designDocContent = DesignDoc(views=Some(Map(viewName -> MapReduce(
      """
        function(doc) {
          emit(doc.company, null);
          emit("total", null);
        }
      """,
      "_count",
      dbCopyDb))))
      val designDoc = new NewDocument(ddName, designDocContent)
      val viewName2 = "bySize"
      val ddName2 = "chained"
      val designDocContent2 = DesignDoc(views = Some(Map(viewName2 -> MapReduce("""
        function(doc) {
          if (doc.key != "total") emit(doc.value, doc.key);  
        }
      """))))
      val designDoc2 = new NewDocument(ddName2, designDocContent2)
      for {
        db <- dbf
        view <- db.createDesign(designDoc, docLogger = SphinxDocLogger("dbCopyViewCreate"))
        docs <- data.create
        _ <- pause(60000)
        dbcopy <- c.getDb(dbCopyDb)
        viewQueryRes <- db.queryView[String, Int](
            ddName,
            viewName,
            flags = ViewQueryFlag(reduce = true, group = true),
            stale = StaleOption.ok)
        queryRes <- dbcopy.allDocs[DbCopyContent[String, Int]](
            flags = ViewQueryFlag(include_docs = true),
            docLogger = SphinxDocLogger("dbCopyAll"))
        view2 <- dbcopy.createDesign(designDoc2, docLogger = SphinxDocLogger("dbCopyView2Create"))
        _ <- pause()
        chainedRes <- dbcopy.queryView[Int, String](
            ddName2,
            viewName2,
            flags = ViewQueryFlag(reduce = false),
            docLogger = SphinxDocLogger("dbCopyView2Query")) 
      } yield {
        val expected = Seq("Amazon" -> 3, "Cloudant" -> 1, "Rackspace" -> 2, "total" -> 6)
        val viewActual = viewQueryRes.rows.map(r => r.key -> r.value)
        assert(viewActual === expected, "view results")
        val actual = queryRes.rows.map(_.doc.get).sortBy(_.key).map(r => r.key -> r.value)
        assert(actual === expected, "dbcopy results")
        
        val expected2 = expected.filter(_._1 != "total").sortBy(_._2).map{case (k,v) => v -> k}.toList
        val actual2 = chainedRes.rows.map(r => r.key -> r.value)
        assert(actual2 === expected2, "chained view results")
      }
    })
  }  
}