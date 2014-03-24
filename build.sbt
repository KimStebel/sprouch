name := "sprouch-cloudant"

version := "0.5.12"

scalaVersion := "2.10.3"

testOptions in Test := Nil

parallelExecution in Test := false

scalacOptions in ThisBuild ++= Seq(
  "-language:implicitConversions",
  "-language:postfixOps",
  "-feature",
  "-unchecked",
  "-deprecation")

resolvers ++= Seq(
    "spray repo" at "http://repo.spray.io",
    "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/")

libraryDependencies ++= Seq(
    "org.scalatest"      %% "scalatest"        % "2.1.0"   % "test",
    "com.novocode"        % "junit-interface"  % "0.10-M1" % "test",
    "io.spray"            % "spray-can"        % "1.3.1",
    "io.spray"            % "spray-client"     % "1.3.1",
    "io.spray"           %%  "spray-json"      % "1.2.5",
    "com.typesafe.akka"  %% "akka-actor"       % "2.3.0")

publishTo := Some(Resolver.file(
    "gh-pages",
    new File("/home/k/workspaces/sprouch-pages/repository/")))