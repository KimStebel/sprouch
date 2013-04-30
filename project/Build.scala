import sbt._
import Keys._

object ApplicationBuild extends Build {

  val dependencies = Seq(
    "org.scalatest" %% "scalatest" % "2.0.M5b" % "test",
    "io.spray" % "spray-can" % "1.1-M7",
    "io.spray" % "spray-client" % "1.1-M7",
    "io.spray" %%  "spray-json" % "1.2.3",
    "com.typesafe.akka" %% "akka-actor" % "2.1.0",
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
    (scalaVersion := "2.10.1"),
    (scalacOptions += "-language:_"),
    (version := "0.5.11")
  ))

}
