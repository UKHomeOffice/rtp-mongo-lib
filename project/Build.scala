import io.gatling.sbt.GatlingPlugin
import sbt.Keys._
import sbt._
import sbtrelease.ReleasePlugin
import spray.revolver.RevolverPlugin._

object Build extends Build {
  val moduleName = "rtp-mongo-lib"

  val root = Project(id = moduleName, base = file(".")).enablePlugins(GatlingPlugin, ReleasePlugin)
    .configs(IntegrationTest)
    .settings(Revolver.settings)
    .settings(Defaults.itSettings: _*)
    //.settings(javaOptions += "-Dconfig.resource=application.test.conf")
    .settings(run := (run in Runtime).evaluated) // Required to stop Gatling plugin overriding the default "run".
    .settings(
      name := moduleName,
      organization := "uk.gov.homeoffice",
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
        "Sonatype Releases" at "https://oss.sonatype.org/content/repositories/releases/",
        "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
        "Kamon Repository" at "http://repo.kamon.io"
      )
    )
    .settings(libraryDependencies ++= {
      val `gatling-verson` = "2.2.2"
      val `reactivemongo-version` = "0.11.9"
      val `casbah-version` = "3.1.1"
      val `salat-version` = "1.11.2"
      val `mongoquery-version` = "0.5"
      val `rtp-io-lib-version` = "1.9.10"
      val `rtp-test-lib-version` = "1.4.3"

      Seq(
        "org.reactivemongo" %% "reactivemongo" % `reactivemongo-version` withSources(),
        "org.mongodb" %% "casbah-core" % `casbah-version` withSources(),
        "org.mongodb" %% "casbah-gridfs" % `casbah-version` withSources(),
        "com.github.salat" %% "salat-core" % `salat-version`,
        "com.github.salat" %% "salat-util" % `salat-version`,
        "com.github.limansky" %% "mongoquery-casbah" % `mongoquery-version` withSources(),
        "com.github.limansky" %% "mongoquery-reactive" % `mongoquery-version` withSources(),
        "de.flapdoodle.embed" % "de.flapdoodle.embed.mongo" % "1.50.5" withSources(),
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