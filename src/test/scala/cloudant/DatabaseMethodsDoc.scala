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
    val dbName = randomDbName
    await(for {
      _ <- ignoreFailure(c.createDb(dbName))
      db <- c.getDb(dbName, docLogger = dl)
    } yield {
      assert(db.name === dbName)
    })
    
  }
  
  test("get all dbs") {
    val dl = SphinxDocLogger("allDbs")
    val dbName = randomDbName
    await(for {
      _ <- ignoreFailure(c.createDb(dbName))
      all <- c.allDbs()
      
    } yield {
      assert(all.contains(dbName), "list of all dbs includes " + dbName)
      assert(all.size > 1, "at least 2 dbs")
    })
  }
  
  test("create db") {
    val dl = SphinxDocLogger("DbPut")
    val dbName = randomDbName
    await(for {
      _ <- ignoreFailure(c.deleteDb(dbName))
      db <- c.createDb(dbName, docLogger = dl)
    } yield {
      assert(db.name === dbName)
    })
  }
  
  test("delete db") {
    val dl = SphinxDocLogger("DbDelete")
    val dbName = randomDbName
    await(for {
      _ <- ignoreFailure(c.createDb(dbName))
      ok <- c.deleteDb(dbName, docLogger = dl)
    } yield {
      assert(ok.ok)
    })
  }
  
  test("get all docs") {
    val dbName = randomDbName
    await(for {
      _ <- ignoreFailure(c.deleteDb(dbName))
      _ <- wait(2000)
      db <- c.createDb(dbName)
      _ <- wait(2000)
      docs <- db.bulkPut(
          (0 to 2).map(n => NewDocument(randomPerson())),
          docLogger = SphinxDocLogger("bulkDocs")
      )
      newDocs <- db.bulkPut(
        docs.map(_.updateData(_.copy(gender="female"))),
        docLogger = SphinxDocLogger("bulkDocs2")
      )
      _ <- wait(2000)
      all <- db.allDocs[Person](
        flags = ViewQueryFlag(include_docs = false),
        docLogger = SphinxDocLogger("allDocs")
      )
    } yield {
      assert(docs.map(_.id).toSet === all.rows.map(_.id).toSet)
    }) 
  }
}
