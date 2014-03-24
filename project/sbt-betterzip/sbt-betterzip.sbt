name := "sbt-betterzip"

organization := "com.secdec"

version := "0.1"

scalaVersion := "2.9.2"

crossScalaVersions := Seq("2.9.1", "2.9.2")

sbtPlugin := true

libraryDependencies += "org.apache.commons" % "commons-compress" % "1.6"
