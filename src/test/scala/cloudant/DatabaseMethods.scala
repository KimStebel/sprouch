package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import docLogger._
import org.scalatest.Tag

class DatabaseMethods extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  implicit val dispatcher = actorSystem.dispatcher
  
  test("get db") {
    val dl = MdDocLogger("DbGet")
    val dbName = randomDbName
    await(for {
      _ <- ignoreFailure(c.createDb(dbName))
      _ <- pause()
      db <- c.getDb(dbName, docLogger = dl)
    } yield {
      assert(db.name === dbName)
    })
    
  }
  
  test("get all dbs") {
    val dl = MdDocLogger("allDbs")
    val dbName = randomDbName
    await(for {
      _ <- ignoreFailure(c.createDb(dbName))
      _ <- pause()
      all <- c.allDbs()
    } yield {
      assert(all.contains(dbName), "list of all dbs includes " + dbName)
      assert(all.size > 1, "at least 2 dbs")
    })
  }
  
  test("create db") {
    val dl = MdDocLogger("DbPut")
    val dbName = randomDbName
    await(for {
      _ <- ignoreFailure(c.deleteDb(dbName))
      db <- c.createDb(dbName, docLogger = dl)
    } yield {
      assert(db.name === dbName)
    })
  }
  
  test("delete db") {
    val dl = MdDocLogger("DbDelete")
    val dbName = randomDbName
    await(for {
      _ <- ignoreFailure(c.createDb(dbName))
      _ <- pause()
      ok <- c.deleteDb(dbName, docLogger = dl)
    } yield {
      assert(ok.ok)
    })
  }
  
  test("get all docs") {
    val dbName = randomDbName
    await(for {
      _ <- ignoreFailure(c.deleteDb(dbName))
      _ <- pause()
      db <- c.createDb(dbName)
      _ <- pause()
      docs <- db.bulkPut(
          (0 to 2).map(n => NewDocument(randomPerson())),
          docLogger = MdDocLogger("bulkDocs")
      )
      newDocs <- db.bulkPut(
        docs.map(_.updateData(_.copy(gender="female"))),
        docLogger = MdDocLogger("bulkDocs2")
      )
      _ <- pause()
      all <- db.allDocs[Person](
        flags = ViewQueryFlag(include_docs = false),
        docLogger = MdDocLogger("allDocs")
      )
    } yield {
      assert(docs.map(_.id).toSet === all.rows.map(_.id).toSet)
    }) 
  }
}
