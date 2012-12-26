package sprouch

import akka.dispatch.Future
import JsonProtocol._

trait AbstractCouch[D <: AbstractDatabase] {
  
  /**
   * Creates a new database. Fails if the database already exists.
   */
  def createDb(dbName:String):Future[D]
  /**
   * Deletes a database and all containing documents.
   */
  def deleteDb(dbName:String):Future[OkResponse]
  /**
   * Looks up a database by its name.
   */
  def getDb(dbName:String):Future[D]

}