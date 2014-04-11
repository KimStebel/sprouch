import sbt._
import Keys._

import  org.apache.commons.codec.binary._

object ApplicationBuild extends Build {

  val dependencies = Seq(
    "org.scalatest" %% "scalatest" % "1.9.2" % "test",
    "io.spray" % "spray-can" % "1.0-RC3",
    "io.spray" % "spray-client" % "1.0-RC3",
    "io.spray" %  "spray-json_2.9.3" % "1.2.5",
    "com.typesafe.akka" % "akka-actor" % "2.0.5",
    "com.novocode" % "junit-interface" % "0.10-M1" % "test",
    "rhino" % "js" % "1.7R2",
    "commons-codec" % "commons-codec" % "1.8"
  )

  val main = Project(id = "sprouch-cloudant", base = new File("."), settings = Project.defaultSettings ++ Seq(
    (scalaVersion := "2.9.3"),
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
    (version := "0.5.11"),
    (parallelExecution in Test := false)
  ))

}
