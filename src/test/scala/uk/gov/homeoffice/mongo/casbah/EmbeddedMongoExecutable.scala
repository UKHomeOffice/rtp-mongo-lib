package uk.gov.homeoffice.mongo.casbah

import java.util.concurrent.TimeUnit
import de.flapdoodle.embed.mongo.config.{MongodConfigBuilder, Net, RuntimeConfigBuilder}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{Command, MongodStarter}
import de.flapdoodle.embed.process.config.io.ProcessOutput
import de.flapdoodle.embed.process.runtime.Network._

trait EmbeddedMongoExecutable {
  lazy val network: Net = {
    def freeServerPort: Int = {
      val port = getFreeServerPort

      if ((27017 to 27027) contains port) {
        TimeUnit.MILLISECONDS.sleep(10)
        freeServerPort
      } else {
        port
      }
    }

    new Net(freeServerPort, localhostIsIPv6)
  }

  lazy val mongodConfig = new MongodConfigBuilder()
    .version(Version.Main.PRODUCTION)
    .net(network)
    .build

  lazy val runtimeConfig = new RuntimeConfigBuilder()
    .defaults(Command.MongoD)
    .processOutput(ProcessOutput.getDefaultInstanceSilent)
    .build()

  lazy val runtime = MongodStarter.getInstance(runtimeConfig)

  lazy val mongodExecutable = runtime.prepare(mongodConfig)
}