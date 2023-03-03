import com.typesafe.sbt.packager.docker._

dockerExposedPorts := Seq(9000)
dockerCommands ++= Seq(
  Cmd("ENV", "JAVA_OPTS=-Dplay.server.pidfile.path=/dev/null"),
  ExecCmd("ENTRYPOINT", "/opt/docker/bin/chatbot")
)

name := "chatbot"

version := "1.0"

lazy val `chatbot` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/"

scalaVersion := "2.13.1"

val authVersion = "7.0.2"

routesImport += "de.bot.common.Binders._"

resolvers += "Sonatype snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"
resolvers += "Atlassian Releases" at "https://maven.atlassian.com/public/"

libraryDependencies ++= Seq(ws, specs2 % Test, guice, ehcache, filters,
  "org.typelevel" %% "cats-effect" % "3.1.1",
  "org.reactivemongo" %% "play2-reactivemongo" % Common.playReactiveMongoVersion,
  "org.reactivemongo" %% "reactivemongo-play-json-compat" % Common.playReactiveMongoVersion,
  "org.reactivemongo" %% "reactivemongo" % Common.ReactivemongoVersion,
  "com.bot4s" %% "telegram-core" % "5.3.0",
  "com.bot4s" %% "telegram-akka" % "5.3.0",
  "io.github.honeycomb-cheesecake" %% "play-silhouette" % authVersion,
  "io.github.honeycomb-cheesecake" %% "play-silhouette-password-bcrypt" % authVersion,
  "io.github.honeycomb-cheesecake" %% "play-silhouette-persistence" % authVersion,
  "io.github.honeycomb-cheesecake" %% "play-silhouette-crypto-jca" % authVersion,
  "io.github.honeycomb-cheesecake" %% "play-silhouette-totp" % authVersion,

  "com.adrianhurt" %% "play-bootstrap" % "1.6.1-P28-B4",
  "org.webjars" %% "webjars-play" % "2.8.13",
  "org.webjars" % "bootstrap" % "5.1.1" exclude("org.webjars", "jquery"),
  "org.webjars" % "jquery" % "3.6.0",
  "com.iheart" %% "ficus" % "1.5.1",
  "net.codingwell" %% "scala-guice" % "5.0.2",
  "com.typesafe.play" %% "play-mailer" % "7.0.1",
  "com.typesafe.play" %% "play-mailer-guice" % "7.0.1",


)
