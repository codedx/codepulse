name := "sbt-betterzip"

organization := "com.secdec"

version := "0.1"

sbtPlugin := true

libraryDependencies += "org.apache.commons" % "commons-compress" % "1.9"
libraryDependencies += "commons-io" % "commons-io" % "2.4"

scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature")
