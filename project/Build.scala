import sbt._
import Keys._

object ApplicationBuild extends Build {

  val dependencies = Seq(
    "org.scalatest" %% "scalatest" % "1.8" % "test",
    "io.spray" % "spray-can" % "1.0-M5",
    "io.spray" % "spray-client" % "1.0-M5",
    "io.spray" %  "spray-json_2.9.2" % "1.2.2",
    "com.typesafe.akka" % "akka-actor" % "2.0",
    "com.novocode" % "junit-interface" % "0.10-M1" % "test"
  )

  val main = Project(id = "sprouch", base = new File("."), settings = Project.defaultSettings ++ Seq(
    (libraryDependencies ++= dependencies),
    (resolvers ++= Seq(
        "spray repo" at "http://repo.spray.io",
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
    )),
    (testOptions in Test := Nil),
    (publishTo := Some(Resolver.file(
        "gh-pages",
        new File("/home/k/workspaces/sprouch-pages/repository/")
    ))),
    (version := "0.5.1")
  ))

}
