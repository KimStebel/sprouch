package sprouch

import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import akka.actor.ActorSystem
import java.util.UUID
import spray.httpx.SprayJsonSupport._
import spray.json._
import spray.httpx.marshalling.Marshaller
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await

@RunWith(classOf[JUnitRunner])
class CouchSuite extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  import actorSystem.dispatcher
    
  test("ssl enabled") {
    val conf = ConfigFactory.load()
    val sslEnabled = conf.getBoolean("spray.can.client.ssl-encryption")
    assert(sslEnabled, "ssl not enabled in config")
  }
  
  test("create, get, and delete db") {
    val dbName = "tempdb" + UUID.randomUUID.toString.toLowerCase
    val f = for {
      createRes <- c.createDb(dbName)
      db <- c.getDb(dbName)
      deleteRes <- c.deleteDb(dbName)
    } yield {
      deleteRes
    }
    await(f)
  }
  
  test("create and get document with / in id") {
    withNewDb(db => {
      val doc = new NewDocument("abc/de", Test(0, ""))
      for {
        _ <- db.createDoc(doc)
        res <- db.getDoc[Test]("abc/de")
      } yield {
        res
      }
    })
  }
  
  test("get nonexistant database fails") {
    intercept[SprouchException] { 
      await(c.getDb("does-not-exist"))
    }
  }
  
  test("retrieve range of documents") {
    val idPrefix = "12345"
    val res = withNewDb(db => {
      (1 to 20).map(n => {
        val doc = new NewDocument(idPrefix + n, Empty)
        db.createDoc(doc)
      }).foreach(f => {
        Await.result(f, testDuration)
      })
      db.allDocs[Empty.type](startKey = Some("12345"), endKey = Some("12346"))
    })
    assert(res.rows.size === 20)
  }
  
  test("update document with older rev fails, current rev succeeds") {
    withNewDb(db => {
      val data = Test(0, "")
      for {
        doc0 <- db.createDoc(data)
        update1 <- db.updateDoc(doc0.updateData(_.copy(foo=1)))
        update2 <- db.updateDoc(doc0.updateData(_.copy(foo=2))).failed
        update3 <- db.updateDoc(update1.updateData(_.copy(foo=2)))
      } yield {
        assert(update3.data.foo === 2)
        update2 match {
          case SprouchException(ErrorResponse(status,_)) => assert(status === 409)
        }
      }
    })
  }
  
  test("create, get, update, and delete document") {
    withNewDb(db => {
      val data = Test(0, "bar")
      for {
        firstDoc <- db.createDoc(data)
        foo1 = firstDoc.updateData(_.copy(foo = 1))
        updatedDoc <- db.updateDoc(foo1)
        gottenDoc <- db.getDoc[Test](firstDoc.id)
        res <- db.deleteDoc(updatedDoc)
      } yield {
        assert(firstDoc.data.foo == 0)
        assert(firstDoc.data.bar == "bar")
        assert(updatedDoc.data.foo == 1)
        assert(gottenDoc.data.foo == 1)
        assert(gottenDoc.data.bar == "bar")
      }
    })
  }
  
  test("getDoc with nonexistant id") {
    import sprouch.dsl.enhanceFuture
    withNewDb(implicit db => {
      for {
        doc <- db.getDoc[Test]("nope").either
        doc2 <- db.getDoc[Test]("nope").option
      } yield {
        assert(doc.isLeft)
        assert(doc.left.get match {
          case se:SprouchException => se.error.status == 404
          case _ => false
        })
        assert(doc2.isEmpty)
      }
    })
  }
}