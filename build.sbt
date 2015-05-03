name := "reactive"

version := "1.0.0"

scalaVersion := "2.11.5"

scalacOptions ++= Seq("-deprecation", "-feature")

(fork in Test) := false

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.12.2"

libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.9.2"

