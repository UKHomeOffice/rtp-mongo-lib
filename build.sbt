import sbt.Keys._
import sbt._

val root = Project(id = "rtp-mongo-lib", base = file("."))
  .enablePlugins(GitVersioning)
  .settings(
    name := "rtp-mongo-lib",
    organization := "uk.gov.homeoffice",
    scalaVersion := "3.3.5",
    crossScalaVersions := Seq("2.13.16","3.3.5"),
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.5.18",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "io.netty"         % "netty-all" % "4.2.2.Final",

      ("org.mongodb.scala" %% "mongo-scala-driver" % "5.5.1").cross(CrossVersion.for3Use2_13),

      // Json support
      "io.circe" %% "circe-core" % "0.14.14",
      "io.circe" %% "circe-generic" % "0.14.14",
      "io.circe" %% "circe-parser" % "0.14.14",

      // cats effect and streaming support
      "org.typelevel" %% "cats-effect" % "3.6.1",
      "co.fs2" %% "fs2-core" % "3.12.0",

      // joda datetime support
      "joda-time" % "joda-time" % "2.14.0",

      // Specs2 support
      "org.specs2" %% "specs2-core" % "4.21.0" withSources(),
      "org.specs2" %% "specs2-matcher-extra" % "4.21.0" withSources(),
      "org.specs2" %% "specs2-junit" % "4.21.0" withSources(),

    ),
    libraryDependencies ++= {
      if (scalaVersion.value.startsWith("3.")) Seq.empty
      else Seq(
        compilerPlugin(("org.typelevel" %% "kind-projector" % "0.13.3").cross(CrossVersion.full))
      )
    },
    // activate kind-projector in Scala 3
    scalacOptions ++= {
      if (scalaVersion.value.startsWith("3.")) Seq("-Ykind-projector")
      else Seq.empty
    }
  )

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
ThisBuild / semanticdbVersion := "4.13.4"

resolvers ++= Seq(
  "ACPArtifactory Lib Snapshot" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/libs-snapshot-local/",
  "ACPArtifactory Lib Release" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/libs-release-local/",
  "ACPArtifactory Ext Release" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/ext-release-local/"
)


publishTo := {
  val artifactory = sys.env.get("ARTIFACTORY_SERVER").getOrElse("https://artifactory.registered-traveller.homeoffice.gov.uk/")
  Some("release"  at artifactory + "artifactory/libs-release-local")
}

Test / packageBin / publishArtifact := true
Test / packageDoc / publishArtifact := true
Test / packageSrc / publishArtifact := true

Compile / run / fork := true
Test / run / fork := true

git.useGitDescribe := true
git.gitDescribePatterns := Seq("v*.*")
git.gitTagToVersionNumber := { tag :String =>

val branchTag = if (git.gitCurrentBranch.value == "master") "" else "-" + git.gitCurrentBranch.value
val uncommit = if (git.gitUncommittedChanges.value) "-U" else ""

tag match {
  case v if v.matches("v\\d+.\\d+") => Some(s"$v.0${uncommit}".drop(1))
  case v if v.matches("v\\d+.\\d+-.*") => Some(s"${v.replaceFirst("-",".")}${branchTag}${uncommit}".drop(1))
  case _ => None
}}

