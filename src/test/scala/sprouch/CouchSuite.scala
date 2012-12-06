package sprouch

import org.scalatest.FunSuite
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import akka.actor.ActorSystem
import akka.dispatch.Await
import akka.util.Duration
import java.util.UUID
import spray.httpx.SprayJsonSupport._
import spray.json._
import akka.dispatch.Future
import spray.httpx.marshalling.Marshaller
import com.typesafe.config.ConfigFactory

@RunWith(classOf[JUnitRunner])
class CouchSuite extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
    
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
      List(deleteRes, createRes, db).foreach(res => assert(res.isRight, res))
    }
    val r = Await.result(f, testDuration)
  }
  
  test("create and get document with / in id") {
    withNewDb(db => {
      val doc = new NewDocument("abc/de", Test(0, ""))
      for {
        createRes <- db.createDoc(doc)
        getRes <- db.getDoc[Test]("abc/de")
      } yield {
        assert(createRes.isRight)
        assert(getRes.isRight)
      }
    })
  }
  
  test("get nonexistant database fails") {
    val getRes = await(c.getDb("does-not-exist"))
    assert(getRes.isLeft)
  }
  
  test("retrieve range of documents") {
    val idPrefix = "12345"
    val res = withNewDb(db => {
      (1 to 20).map(n => {
        val doc = new NewDocument(idPrefix + n, Empty)
        db.createDoc(doc).map(_.right.get)
      }).foreach(f => {
        Await.result(f, testDuration)
      })
      db.allDocs[Empty.type](Some("12345"), Some("12346"))
    })
    assert(res.right.get.total_rows === 20)
  }
  
  test("update document with older rev fails, current rev succeeds") {
    withNewDb(db => {
      val data = Test(0, "")
      for {
        doc0 <- db.createDoc(data).map(_.right.get)
        update1 <- db.updateDoc(doc0.updateData(_.copy(foo=1)))
        update2 <- db.updateDoc(doc0.updateData(_.copy(foo=2)))
        update3 <- db.updateDoc(update1.right.get.updateData(_.copy(foo=2)))
      } yield {
        assert(update1.isRight)
        assert(update2.isLeft)
        assert(update3.isRight)
        assert(update3.right.get.data.foo === 2)
      }
    })
  }
  
  test("create, get, update, and delete document") {
    withNewDb(db => {
      val data = Test(0, "bar")
      for {
        firstRes <- db.createDoc(data)
        val _ = assert(firstRes.isRight, firstRes)
        val firstDoc = firstRes.right.get
        val foo1 = firstDoc.updateData(_.copy(foo = 1))
        updatedDoc <- db.updateDoc(foo1).map(_.right.get)
        gottenDoc <- db.getDoc[Test](firstDoc.id).map(_.right.get)
        res <- db.deleteDoc(updatedDoc)
      } yield {
        assert(res.isRight, res)
        assert(firstDoc.data.foo == 0)
        assert(firstDoc.data.bar == "bar")
        assert(updatedDoc.data.foo == 1)
        assert(gottenDoc.data.foo == 1)
        assert(gottenDoc.data.bar == "bar")
      }
    })
  } 
}