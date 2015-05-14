name := "reactive"

version := "1.0.0"

scalaVersion := "2.11.5"

scalacOptions ++= Seq("-deprecation", "-feature")

(fork in Test) := false

libraryDependencies ++= Seq(
  "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test",
  "org.scalacheck" %% "scalacheck" % "1.12.2",
  "org.scala-lang.modules" %% "scala-async" % "0.9.2",
  "io.reactivex" %% "rxscala" % "0.23.0",
  "org.scala-lang.modules" %% "scala-swing" % "1.0.1",
  "io.reactivex" % "rxswing" % "0.21.0",
  "org.json4s" %% "json4s-native" % "3.2.11",
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0",
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  "com.squareup.retrofit" % "retrofit" % "1.0.0",
  "com.typesafe.akka" %% "akka-actor" % "2.3.9",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9",
  "com.typesafe.akka" %% "akka-persistence-experimental" % "2.3.9")
