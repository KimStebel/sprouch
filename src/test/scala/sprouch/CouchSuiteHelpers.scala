package sprouch

import org.scalatest.FunSuite
import akka.actor.ActorSystem
import akka.util.Duration
import akka.dispatch.Await
import akka.dispatch.Future
import java.util.UUID
import sprouch.json.Schema
import spray.json.JsonParser
import java.net.URL

case class Test(foo:Int, bar:String)

trait CouchSuiteHelpers {
  self:FunSuite =>
    
  import JsonProtocol._
  
  implicit val testFormat = jsonFormat2(Test)
  
  case class Person(name:String, age:Int, gender:String)
  implicit val personFormat = jsonFormat3(Person)
  val personSchema = Schema.complexSchemaFormat.read(JsonParser("""
  {
    "name": { "type": "name" },
    "age": { "type": "int", "min": 18, "max": 120 },
    "gender": { "type": "choice", "values": ["male", "female"] }
  }"""))
  def randomPerson() = personFormat.read(personSchema.generate())
  
  implicit val actorSystem = ActorSystem("MySystem")
  val url = new URL(System.getenv("TESTY_DB_URL"))
  val host = url.getHost
  val dbBaseName = url.getPath.replaceAll("/", "")
  val https = url.getProtocol.toLowerCase == "https"
  val user = System.getenv("TESTY_DB_ADMIN_USER")
  val pass = System.getenv("TESTY_DB_ADMIN_PASS")
  val up = if (user.trim == "") None else Some(user -> pass)
  val port = url.getPort match {
    case -1 => if (https) 443 else 80
    case p => p
  }
  private val conf = Config()(actorSystem, host, port, up, https) 
  val c = new Couch(conf)
  val cSync = sprouch.synchronous.Couch(conf)
  implicit val testDuration = Duration("300 seconds")
  def await[A](f:Future[A]) = Await.result(f, testDuration)
  
  def assertGet[A](e:Either[_,A]):A = {
    assert(e.isRight, e)
    e.right.get
  }
  
  def ignoreFailure[A](f: =>Future[A]) = f recover { case _ => } 
  
  def pause(ms:Int = 2000):Future[Unit] = Future {
    Thread.sleep(ms)
  }
  
  protected def randomDbName = dbBaseName + UUID.randomUUID.toString.toLowerCase
  
  def withNewDb[A](f:Database => Future[A]):A = {
    val dbName = randomDbName
    withNewDb(dbName)(f)
  }
  
  def withNewDb[A](name:String)(f:Database => Future[A]):A = {
    await(for {
      del <- c.deleteDb(name) recover { case _ => }
      db <- c.createDb(name)
      res <- f(db) andThen { case _ => db.delete() }
    } yield (res))
  }
  
  def withNewDbFuture[A](f:Future[Database] => Future[A]):A = {
    val dbName = randomDbName
    println("using database " + dbName)
    val dbf = c.createDb(dbName)
    val resf = f(dbf)
    val res = await(resf)
    await(c.deleteDb(dbName))
    res
  }
  
  def withDbFuture[A](dbName:String)(f:Future[Database] => Future[A]):A = await(for {
    _ <- Future()
    val dbf = c.getDb(dbName)
    res <- f(dbf) andThen { case _ => dbf.flatMap(_.delete()) }
  } yield res)
  
  def withNewDbFuture[A](dbName:String)(f:Future[Database] => Future[A]):A = await(for {
    _ <- c.deleteDb(dbName) recover { case _ => }
    val dbf = c.createDb(dbName)
    res <- f(dbf) andThen { case _ => dbf.flatMap(_.delete()) }
  } yield res)
  
  def withNewDbSync[A](f:sprouch.synchronous.Database => A):A = {
  	val dbName = "tempdb" + UUID.randomUUID.toString.toLowerCase
    val db = cSync.createDb(dbName)
    val res = f(db)
    db.delete
    res
  }
}