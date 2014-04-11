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

class GlobalChanges extends FunSuite with CouchSuiteHelpers {
  import c.GlobalChangesActor._
  import ChangesModule._
    
  test("global changes feed continuous") {
    implicit val dispatcher = actorSystem.dispatcher
    val duration = Duration("60 seconds")
    implicit val timeout = Timeout(duration)
    val uuid = randomDbName
    val dbNamePrefix = "documentationchangescontinuous"
    val dbNames = (1 to 2).map(n => dbNamePrefix + n + uuid)  
    
    class ChangesTestActor extends Actor {
      val log = Logging(context.system, this)
      var testSender:ActorRef = null
      var updates: Set[(String, String)] = Set()
      val cl = c.pipelines.conduit
      
      def receive = {
        case "test" => {
          testSender = sender
          c.GlobalChangesActor.changesActorRef ! LastSeq()
        }
        case LastSeqResponse(seq) => {
          val dl = MdDocLogger("globalChangesFeedContinuous")
          c.GlobalChangesActor.changesActorRef ! Continuous(since = Some(seq))
          for (dbName <- dbNames) {
            c.createDb(dbName).andThen{case _ => c.deleteDb(dbName)} 
          }
        }
        case ResponseEnd => {
          testSender ! updates
        }
        case DbUpdate(dbName, typ, seq, _) => {
          try {
            if (dbName.startsWith(dbNamePrefix)) {
              updates += dbName -> typ
            }
            if (updates.size == 4) {
              testSender ! (updates)
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

    val testActor = actorSystem.actorOf(Props(new ChangesTestActor))
    val actual = Await.result(testActor ? "test", duration).asInstanceOf[Set[(String, String)]]
    actorSystem.stop(testActor)
    val expected = dbNames.flatMap(dbName => Set(dbName -> "created", dbName -> "deleted")).toSet
    assert(actual === expected)
  }
  
  test("global changes feed longpoll") {
    implicit val dispatcher = actorSystem.dispatcher
    import akka.pattern.ask
    import akka.util.{Timeout, Duration}
    val duration = Duration("60 seconds")
    implicit val timeout = Timeout(duration)
    
    class ChangesTestActor extends Actor {
      val log = Logging(context.system, this)
      var testSender:ActorRef = null
      var uuid:String = null
      val cl = c.pipelines.conduit
      import c.GlobalChangesActor._
      import ChangesModule._
      def receive = {
        case uuid:String => {
          this.uuid = uuid
          testSender = sender
          c.GlobalChangesActor.changesActorRef ! LastSeq()
        }
        case LastSeqResponse(seq) => {
          c.GlobalChangesActor.changesActorRef ! Longpoll(timeout = Some(10000), since = Some(seq))
          c.createDb("documentationchanges1" + uuid).andThen{case _ => c.deleteDb("documentationchanges1" + uuid)}
          c.createDb("documentationchanges2" + uuid).andThen{case _ => c.deleteDb("documentationchanges2" + uuid)}
        }
        case DbUpdates(results, _) => {
          val res = results.filter(_.dbname.startsWith("documentation")).map(upd => upd.dbname -> upd.`type`).toSet
          testSender ! res
        }
        case x => {
          log.error("unexpected message received: " + x)
        }
      }
    }

    val testActor = actorSystem.actorOf(Props(new ChangesTestActor))
    val uuid = randomDbName
    val result = Await.result(testActor ? uuid, duration).asInstanceOf[Set[(String, String)]]
    actorSystem.stop(testActor)
    val expected = Set(
        "documentationchanges1" + uuid -> "created",
        "documentationchanges2" + uuid -> "created",
        "documentationchanges1" + uuid -> "deleted",
        "documentationchanges2" + uuid -> "deleted"
    )
    assert(result.subsetOf(expected))
  }
}