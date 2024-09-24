import sbt.Keys._
import sbt._

val root = Project(id = "rtp-mongo-lib", base = file("."))
  .enablePlugins(GitVersioning)
  .settings(
    name := "rtp-mongo-lib",
    organization := "uk.gov.homeoffice",
    scalaVersion := "2.13.14",
    crossScalaVersions := Seq("2.12.16", "2.13.14"),
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.5.6",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      "io.netty"         % "netty-all" % "4.1.112.Final",

      "org.mongodb.scala" %% "mongo-scala-driver" % "5.1.3",

      // Json support
      "io.circe" %% "circe-core" % "0.14.9",
      "io.circe" %% "circe-generic" % "0.14.9",
      "io.circe" %% "circe-parser" % "0.14.9",

      // cats effect and streaming support
      "org.typelevel" %% "cats-effect" % "3.5.4",
      "co.fs2" %% "fs2-core" % "3.10.2",

      // joda datetime support
      "joda-time" % "joda-time" % "2.12.5",

      "uk.gov.homeoffice" %% "rtp-test-lib" % "1.6.22-gacd233d",

      // only required whilst we continue to cross-compile to Scala 2.12
      "org.scala-lang.modules" %% "scala-collection-compat" % "2.12.0"
    ),
    addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.10.3"),
    addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  )

credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

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

