import sbt.Keys._
import sbt._

val root = Project(id = "rtp-mongo-lib", base = file("."))
  .enablePlugins(GitVersioning)
  .settings(
    name := "rtp-mongo-lib",
    organization := "uk.gov.homeoffice",
    scalaVersion := "2.12.6",
    crossScalaVersions := Seq("2.11.8", "2.12.6")
  )

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

resolvers ++= Seq(
  "Artifactory Snapshot Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-snapshot-local/",
  "Artifactory Release Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-release-local/",
  "Artifactory External Release Local Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/ext-release-local/"
)

val `gatling-verson` = "2.2.2"
val `reactivemongo-version` = "0.13.0"
val `casbah-version` = "3.1.1"
val `salat-version` = "1.11.2"
val `mongoquery-version` = "0.6"
val `rtp-test-lib-version` = "1.6.6-g6f56307"

libraryDependencies ++= Seq(
  "org.reactivemongo" %% "reactivemongo" % `reactivemongo-version` withSources(),
  "org.mongodb" %% "casbah-core" % `casbah-version` withSources(),
  "org.mongodb" %% "casbah-gridfs" % `casbah-version` withSources(),
  "com.github.salat" %% "salat-core" % `salat-version`,
  "com.github.salat" %% "salat-util" % `salat-version`,
  "com.github.limansky" %% "mongoquery-casbah" % `mongoquery-version` withSources(),
  "com.github.limansky" %% "mongoquery-reactive" % `mongoquery-version` withSources(),
  "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "1.50.5" withSources(),
  "uk.gov.homeoffice" %% "rtp-io-lib" % "2.2.0",
  "uk.gov.homeoffice" %% "rtp-test-lib" % `rtp-test-lib-version` withSources()
)

publishTo := {
  val artifactory = sys.env.get("ARTIFACTORY_SERVER").getOrElse("http://artifactory.registered-traveller.homeoffice.gov.uk/")
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

