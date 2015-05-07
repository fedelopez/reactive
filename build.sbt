name := "reactive"

version := "1.0.0"

scalaVersion := "2.11.5"

scalacOptions ++= Seq("-deprecation", "-feature")

(fork in Test) := false

libraryDependencies += "org.scalatest" % "scalatest_2.11" % "2.2.1" % "test"

libraryDependencies += "org.scalacheck" %% "scalacheck" % "1.12.2"

libraryDependencies += "org.scala-lang.modules" %% "scala-async" % "0.9.2"

libraryDependencies += "io.reactivex" %% "rxscala" % "0.23.0"

libraryDependencies += "org.scala-lang.modules" %% "scala-swing" % "1.0.1"

libraryDependencies += "io.reactivex" % "rxswing" % "0.21.0"

libraryDependencies += "org.json4s" %% "json4s-native" % "3.2.11"

libraryDependencies += "net.databinder.dispatch" %% "dispatch-core" % "0.11.0"

libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value

libraryDependencies += "com.squareup.retrofit" % "retrofit" % "1.0.0"
