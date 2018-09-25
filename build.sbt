import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin

val root = Project(id = "rtp-mongo-lib", base = file(".")).enablePlugins(ReleasePlugin)
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
val `rtp-test-lib-version` = "1.4.4-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.reactivemongo" %% "reactivemongo" % `reactivemongo-version` withSources(),
  "org.mongodb" %% "casbah-core" % `casbah-version` withSources(),
  "org.mongodb" %% "casbah-gridfs" % `casbah-version` withSources(),
  "com.github.salat" %% "salat-core" % `salat-version`,
  "com.github.salat" %% "salat-util" % `salat-version`,
  "com.github.limansky" %% "mongoquery-casbah" % `mongoquery-version` withSources(),
  "com.github.limansky" %% "mongoquery-reactive" % `mongoquery-version` withSources(),
  "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "1.50.5" withSources(),
  "uk.gov.homeoffice" %% "rtp-io-lib" % "1.9.11-SNAPSHOT",
  "uk.gov.homeoffice" %% "rtp-test-lib" % `rtp-test-lib-version` withSources()
)

publishTo := {

  val artifactory = sys.env.get("ARTIFACTORY_SERVER").getOrElse("http://artifactory.registered-traveller.homeoffice.gov.uk/")

  if (isSnapshot.value)
    Some("snapshot" at artifactory + "artifactory/libs-snapshot-local")
  else
    Some("release"  at artifactory + "artifactory/libs-release-local")
}

// Enable publishing the jar produced by `test:package`
publishArtifact in (Test, packageBin) := true

// Enable publishing the test API jar
publishArtifact in (Test, packageDoc) := true

// Enable publishing the test sources jar
publishArtifact in (Test, packageSrc) := true

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList(ps @ _*) if ps.last endsWith ".java" => MergeStrategy.discard
  case _ => MergeStrategy.first
}

fork in run := true
fork in test := true
