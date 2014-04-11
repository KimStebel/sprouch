package cloudant

import org.scalatest.FunSuite
import akka.dispatch.Future
import spray.json.JsonFormat
import sprouch._
import docLogger._
import sprouch.dsl._
import spray.json.JsonWriter
import spray.json.JsObject
import spray.json.JsonReader

class SearchWithPerFieldAnalyzer extends FunSuite with CouchSuiteHelpers {
  import JsonProtocol._
  
  test("lucene based search with per field analyzer") {
    implicit val dispatcher = actorSystem.dispatcher
        
    case class EnglishGerman(word:String, wordInGerman:String)
    implicit val geformat = jsonFormat2(EnglishGerman)
    
    withNewDbFuture(implicit dbf => {
      val data = Seq(
          EnglishGerman("houses", "Häuser"),
          EnglishGerman("moved", "umgezogen"),
          EnglishGerman("faster", "schneller")
      )
      val perFieldIndex = Index("""
          function(doc){
            index("en", doc.word, {"store": "yes"});
            index("de", doc.wordInGerman, {"store": "yes"});
          }
      """, Some(Analyzer(name = "perfield", default = Some("english"), fields = Some(Map("de" -> "german", "en" -> "english")))))
      val defaultAnalyzerIndex = Index("""
          function(doc){
            index("en", doc.word, {"store": "yes"});
            index("de", doc.wordInGerman, {"store": "yes"});
          }
      """, None)
      val indexes = Indexes(Map("perField" -> perFieldIndex, "defaultAnalyzer" -> defaultAnalyzerIndex))
      val ddocName = "mySearches"
      val indexesDoc = new NewDocument(ddocName, indexes)
      val dl = MdDocLogger("searchWithPerFieldAnalyzer")
      val cidl = MdDocLogger("searchWithPerFieldAnalyzerDesignDoc")
      for {
        db <- dbf
        view <- db.createIndexes(indexesDoc/*, docLogger = cidl*/)
        docs <- data.create
        hausRes <- db.search(ddocName, "perField", "de:Haus")
        houseRes <- db.search(ddocName, "perField", "en:house", docLogger = dl)
        umziehenRes <- db.search(ddocName, "perField", "de:umziehen")
        moveRes <- db.search(ddocName, "perField", "en:move")
        schnellRes <- db.search(ddocName, "perField", "de:schnell")
        fastRes <- db.search(ddocName, "perField", "en:fast")
        daFastRes <- db.search(ddocName, "defaultAnalyzer", "en:fast")
        
      } yield {
        val houseDocId = docs.find(_.data.word == "houses").get.id
        val moveDocId = docs.find(_.data.word == "moved").get.id
        val fastDocId = docs.find(_.data.word == "faster").get.id
        
        Seq(
            hausRes -> "Haus",
            houseRes -> "house",
            //umziehenRes -> "umziehen",
            moveRes -> "move",
            schnellRes -> "schnell"//,
            //fastRes -> "fast",
            //daFastRes -> "fast"
        ).foreach{case (res, word) => {
          assert(res.total_rows === 1, word + " did not yield expected number of search results")
        }}
        assert(hausRes.rows.head.id === houseDocId)
        assert(houseRes.rows.head.id === houseDocId)
        //assert(umziehenRes.rows.head.id === moveDocId)
        assert(moveRes.rows.head.id === moveDocId)
        assert(schnellRes.rows.head.id === fastDocId)
        //assert(fastRes.rows.head.id === fastDocId)
        //assert(daFastRes.rows.head.id === fastDocId)
        
      }
    })
  }
}
