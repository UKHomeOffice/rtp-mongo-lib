resolvers += Classpaths.sbtPluginReleases

addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M14")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.0.4")

addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.0.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.0")

addSbtPlugin("io.gatling" % "gatling-sbt" % "2.2.0")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.8.1")

addSbtPlugin("com.github.gseitz" % "sbt-release" % "1.0.3")