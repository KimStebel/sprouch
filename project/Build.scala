import sbt._
import Keys._

object ApplicationBuild extends Build {

  val dependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.0" % "test",
    "io.spray" % "spray-can" % "1.2.0",
    "io.spray" % "spray-client" % "1.2.0",
    "io.spray" %  "spray-json_2.10" % "1.2.5",
    "com.typesafe.akka" % "akka-actor_2.10" % "2.2.3",
    "com.novocode" % "junit-interface" % "0.10-M1" % "test"
  )

  val main = Project(id = "sprouch", base = new File("."), settings = Project.defaultSettings ++ Seq(
    (scalaVersion := "2.10.3"),
    (libraryDependencies ++= dependencies),
    (resolvers ++= Seq(
        "spray repo" at "http://repo.spray.io",
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
    )),
    (testOptions in Test := Nil),
    (publishTo := Some(Resolver.file(
        "gh-pages",
        new File("/Users/thadeu/Documents/source-codes/open-source/sprouch/pages")
    ))),
    (version := "0.5.11-custom")
  ))

}
