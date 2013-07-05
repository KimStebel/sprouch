package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._

class DatabaseMethodsDoc extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  implicit val dispatcher = actorSystem.dispatcher
  
  test("get db") {
    val dl = SphinxDocLogger("DbGet")
    await(for {
      _ <- ignoreFailure(c.createDb("db"))
      db <- c.getDb("db", docLogger = dl)
    } yield {
      assert(db.name === "db")
    })
    
  }
  
  test("get all dbs") {
    val dl = SphinxDocLogger("allDbs")
    await(for {
      _ <- ignoreFailure(c.createDb("heyho"))
      all <- c.allDbs()
      
    } yield {
      assert(all.contains("heyho"), "list of all dbs includes heyho")
      assert(all.size > 1, "at least 2 dbs")
    })
  }
  
  test("create db") {
    val dl = SphinxDocLogger("DbPut")
    await(for {
      _ <- ignoreFailure(c.deleteDb("db"))
      db <- c.createDb("db", docLogger = dl)
    } yield {
      assert(db.name === "db")
    })
  }
  
  test("delete db") {
    val dl = SphinxDocLogger("DbDelete")
    await(for {
      _ <- ignoreFailure(c.createDb("db"))
      ok <- c.deleteDb("db", docLogger = dl)
    } yield {
      assert(ok.ok)
    })
  }
  
  test("get all docs") {
    await(for {
      _ <- c.deleteDb("test")
      db <- c.createDb("test")
      docs <- db.bulkPut(
          (0 to 2).map(n => NewDocument(randomPerson())),
          docLogger = SphinxDocLogger("bulkDocs")
      )
     
      newDocs <- db.bulkPut(
        docs.map(_.updateData(_.copy(gender="female"))),
        docLogger = SphinxDocLogger("bulkDocs2")
      )
      all <- db.allDocs[Person](
        flags = ViewQueryFlag(include_docs = false),
        docLogger = SphinxDocLogger("allDocs")
      )
      
    } yield {
      assert(docs.map(_.id).toSet === all.rows.map(_.id).toSet)
    })
    
  }
    
}
