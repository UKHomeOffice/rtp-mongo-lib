import io.gatling.sbt.GatlingPlugin
import sbt.Keys._
import sbt._
import spray.revolver.RevolverPlugin._

object Build extends Build {
  val moduleName = "rtp-mongo-lib"

  val root = Project(id = moduleName, base = file(".")).enablePlugins(GatlingPlugin)
    .configs(IntegrationTest)
    .settings(Revolver.settings)
    .settings(Defaults.itSettings: _*)
    //.settings(javaOptions += "-Dconfig.resource=application.test.conf")
    .settings(run := (run in Runtime).evaluated) // Required to stop Gatling plugin overriding the default "run".
    .settings(
      name := moduleName,
      organization := "uk.gov.homeoffice",
      version := "1.7.1",
      scalaVersion := "2.11.8",
      scalacOptions ++= Seq(
        "-feature",
        "-language:implicitConversions",
        "-language:higherKinds",
        "-language:existentials",
        "-language:reflectiveCalls",
        "-language:postfixOps",
        "-Yrangepos",
        "-Yrepl-sync"
      ),
      ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },
      resolvers ++= Seq(
        "Artifactory Snapshot Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-snapshot-local/",
        "Artifactory Release Realm" at "http://artifactory.registered-traveller.homeoffice.gov.uk/artifactory/libs-release-local/",
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
        "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
        "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
        "Kamon Repository" at "http://repo.kamon.io"
      )
    )
    .settings(libraryDependencies ++= {
      val `gatling-verson` = "2.1.7"
      val `rtp-io-lib-version` = "1.7.2-SNAPSHOT"
      val `rtp-test-lib-version` = "1.2.1"

      Seq(
        "com.novus" %% "salat" % "1.9.9",
        "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "1.50.2" withSources(),
        "uk.gov.homeoffice" %% "rtp-io-lib" % `rtp-io-lib-version` withSources(),
        "uk.gov.homeoffice" %% "rtp-test-lib" % `rtp-test-lib-version` withSources()
      ) ++ Seq(
        "io.gatling.highcharts" % "gatling-charts-highcharts" % `gatling-verson` % IntegrationTest withSources(),
        "io.gatling" % "gatling-test-framework" % `gatling-verson` % IntegrationTest withSources(),
        "uk.gov.homeoffice" %% "rtp-io-lib" % `rtp-io-lib-version` % Test classifier "tests" withSources(),
        "uk.gov.homeoffice" %% "rtp-test-lib" % `rtp-test-lib-version` % Test classifier "tests" withSources()
      )
    })
}