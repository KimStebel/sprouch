package cloudant

import org.scalatest.FunSuite

import sprouch._
import JsonProtocol._
import docLogger._

import akka.dispatch.Await
import akka.actor.{Actor,Props}
import akka.event.Logging
import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.{Timeout, Duration}

import java.util.UUID

class Changes extends FunSuite with CouchSuiteHelpers {
  import c.GlobalChangesActor._
  import ChangesModule._
  
  test("db changes feed longpoll") {
    implicit val dispatcher = actorSystem.dispatcher
    import akka.pattern.ask
    import akka.util.{Timeout, Duration}
    val duration = Duration("60 seconds")
    implicit val timeout = Timeout(duration)
    
    class ChangesTestActor extends Actor {
      val log = Logging(context.system, this)
      var testSender:ActorRef = null
      var uuid:String = null
      var db:Database = null
      val cl = c.pipelines.conduit
      import c.GlobalChangesActor._
      import ChangesModule._
      def receive = {
        case uuid:String => {
          this.uuid = uuid
          testSender = sender
          c.createDb(uuid).foreach(db => {
            this.db = db
            db.DbChangesActor.changesActorRef ! LastSeq()
          })
        }
        case LastSeqResponse(seq) => {
          db.DbChangesActor.changesActorRef ! Longpoll(timeout = Some(10000), since = Some(seq))
          db.createDocId("foo", Empty)
        }
        case DocUpdates(results, last_seq, pending) => {
          val res = results.map(_.id).toSet
          testSender ! res
        }
        case x => {
          log.error("unexpected message received: " + x)
        }
      }
    }

    val testActor = actorSystem.actorOf(Props(new ChangesTestActor))
    val uuid = randomDbName
    val result = Await.result(testActor ? uuid, duration).asInstanceOf[Set[String]]
    actorSystem.stop(testActor)
    val expected = Set("foo")
    assert(result === expected)
  }
  
  test("db changes feed continuous") {
    implicit val dispatcher = actorSystem.dispatcher
    import akka.pattern.ask
    import akka.util.{Timeout, Duration}
    val duration = Duration("60 seconds")
    implicit val timeout = Timeout(duration)
    
    class DbChangesTestActor extends Actor {
      val log = Logging(context.system, this)
      var testSender:ActorRef = null
      var updates: Set[String] = Set()
      var uuid:String = null
      var db:Database = null
      val cl = c.pipelines.conduit
      import c.GlobalChangesActor._
      import ChangesModule._
      def receive = {
        case uuid:String => {
          this.uuid = uuid
          testSender = sender
          c.createDb(uuid).foreach(db => {
            this.db = db
            db.DbChangesActor.changesActorRef ! LastSeq()
          })
          
        }
        case LastSeqResponse(seq) => {
          val dl = MdDocLogger("dbChangesFeed")
          db.DbChangesActor.changesActorRef ! Continuous(since = Some(seq))
          db.createDocId("1" + uuid, Empty).flatMap{doc => db.deleteDoc(doc)}
          db.createDocId("2" + uuid, Empty).flatMap{doc => db.deleteDoc(doc)}
        }
        case ResponseEnd => {
          testSender ! updates
        }
        case DocUpdate(_, id, _) => {
          try {
            updates += id
            if (updates.size == 2) {
              testSender ! updates
            }
          } catch {
            case e => {
              //not an update chunk, ignore
            }
          }
        }
        case x => {
          log.error("unexpected message received: " + x)
        }
      }
    }

    val testActor = actorSystem.actorOf(Props(new DbChangesTestActor))
    val uuid = randomDbName
    val result = Await.result(testActor ? uuid, duration).asInstanceOf[Set[String]]
    actorSystem.stop(testActor)
    val expected = Set(
        "1" + uuid,
        "2" + uuid
    )
    assert(result === expected)

  }

}