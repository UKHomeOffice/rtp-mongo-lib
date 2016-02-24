import io.gatling.sbt.GatlingPlugin
import sbt.Keys._
import sbt._
import spray.revolver.RevolverPlugin._

object Build extends Build {
  val moduleName = "rtp-mongo-lib"

  lazy val mongo = Project(id = moduleName, base = file(".")).enablePlugins(GatlingPlugin)
    .configs(IntegrationTest)
    .settings(Revolver.settings)
    .settings(Defaults.itSettings: _*)
    .settings(
      name := moduleName,
      organization := "uk.gov.homeoffice",
      version := "1.3.0-SNAPSHOT",
      scalaVersion := "2.11.7",
      scalacOptions ++= Seq(
        "-feature",
        "-language:implicitConversions",
        "-language:higherKinds",
        "-language:existentials",
        "-language:reflectiveCalls",
        "-language:postfixOps",
        "-Yrangepos",
        "-Yrepl-sync"),
      ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
      resolvers ++= Seq(
        "Artifactory Snapshot Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-snapshot-local/",
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
        "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
        "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
        "Kamon Repository" at "http://repo.kamon.io"),
      libraryDependencies ++= Seq(
        "com.novus" %% "salat" % "1.9.9",
        "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "1.50.2" withSources(),
        "uk.gov.homeoffice" %% "rtp-io-lib" % "1.2.0-SNAPSHOT" withSources(),
        "uk.gov.homeoffice" %% "rtp-test-lib" % "1.2.0-SNAPSHOT" withSources()
      ),
      libraryDependencies ++= Seq(
        "uk.gov.homeoffice" %% "rtp-io-lib" % "1.2.0-SNAPSHOT" % Test classifier "tests" withSources(),
        "uk.gov.homeoffice" %% "rtp-test-lib" % "1.2.0-SNAPSHOT" % Test classifier "tests" withSources()
      )
    )
    //.settings(javaOptions += "-Dconfig.resource=application.test.conf")
    .settings(run := (run in Runtime).evaluated) // Required to stop Gatling plugin overriding the default "run".
}