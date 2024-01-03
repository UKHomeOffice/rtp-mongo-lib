import sbt.Keys._
import sbt._

val root = Project(id = "rtp-mongo-lib", base = file("."))
  .enablePlugins(GitVersioning)
  .settings(
    name := "rtp-mongo-lib",
    organization := "uk.gov.homeoffice",
    scalaVersion := "2.12.16",
    crossScalaVersions := Seq("2.12.16")
  )

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

resolvers ++= Seq(
  "ACPArtifactory Lib Snapshot" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/libs-snapshot-local/",
  "ACPArtifactory Lib Release" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/libs-release-local/",
  "ACPArtifactory Ext Release" at "https://artifactory.digital.homeoffice.gov.uk/artifactory/ext-release-local/"
)

val json4sVersion = "3.6.12"

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-classic" % "1.4.14",
  "org.mongodb" %% "casbah-core" % "3.1.1" withSources(),
  "org.mongodb" %% "casbah-gridfs" % "3.1.1" withSources(),
  "com.github.salat" %% "salat-core" % "1.11.2",
  "com.github.salat" %% "salat-util" % "1.11.2",

  "org.json4s" %% "json4s-core" % json4sVersion,
  "org.json4s" %% "json4s-native" % json4sVersion,
  "org.json4s" %% "json4s-jackson" % json4sVersion,
  "org.json4s" %% "json4s-ext" % json4sVersion,
  "org.json4s" %% "json4s-mongo" % json4sVersion,

  "uk.gov.homeoffice" %% "rtp-io-lib" % "2.2.22-ga39707a" excludeAll ExclusionRule(organization = "org.json4s"),
  "uk.gov.homeoffice" %% "rtp-test-lib" % "1.6.22-gacd233d"
)

publishTo := {
  val artifactory = sys.env.get("ARTIFACTORY_SERVER").getOrElse("https://artifactory.registered-traveller.homeoffice.gov.uk/")
  Some("release"  at artifactory + "artifactory/libs-release-local")
}

publishArtifact in (Test, packageBin) := true
publishArtifact in (Test, packageDoc) := true
publishArtifact in (Test, packageSrc) := true

fork in run := true
fork in test := true

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

