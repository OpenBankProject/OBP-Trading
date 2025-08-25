name := "obp-trading"

organization := "com.openbankproject"

version := "0.1.0-SNAPSHOT"

scalaVersion := "2.13.15"

lazy val http4sVersion = "0.23.24"
lazy val slickVersion = "3.4.1"
lazy val liftVersion = "3.5.0"

resolvers ++= Seq(
  "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/",
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository"
)

libraryDependencies ++= Seq(
  // OBP Commons
  "com.openbankproject" %% "obp-commons" % "2024.1.0-SNAPSHOT",
  
  // Http4s (like OBP-API-II)
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,
  
  // Lift Web (for OBP compatibility)
  "net.liftweb" %% "lift-webkit" % liftVersion,
  "net.liftweb" %% "lift-mapper" % liftVersion,
  "net.liftweb" %% "lift-json" % liftVersion,
  "net.liftweb" %% "lift-util" % liftVersion,
  "net.liftweb" %% "lift-common" % liftVersion,
  
  // Cats Effect (for http4s)
  "org.typelevel" %% "cats-effect" % "3.5.2",
  "org.typelevel" %% "cats-core" % "2.10.0",
  
  // JSON
  "io.circe" %% "circe-core" % "0.14.6",
  "io.circe" %% "circe-generic" % "0.14.6",
  "io.circe" %% "circe-parser" % "0.14.6",
  "io.circe" %% "circe-literal" % "0.14.6",
  
  // Database
  "com.typesafe.slick" %% "slick" % slickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
  "org.postgresql" % "postgresql" % "42.7.1",
  "com.zaxxer" % "HikariCP" % "5.1.0",
  "org.flywaydb" % "flyway-core" % "9.22.3",
  
  // Redis
  "dev.profunktor" %% "redis4cats-effects" % "1.5.2",
  "dev.profunktor" %% "redis4cats-streams" % "1.5.2",
  
  // Configuration
  "com.typesafe" % "config" % "1.4.3",
  "com.github.pureconfig" %% "pureconfig" % "0.17.4",
  
  // Message Brokers
  "dev.zio" %% "zio-kafka" % "2.7.2",
  "com.github.fd4s" %% "fs2-rabbit" % "5.1.0",
  
  // Logging
  "ch.qos.logback" % "logback-classic" % "1.4.14",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "org.slf4j" % "slf4j-api" % "2.0.9",
  
  // Authentication & JWT
  "com.github.jwt-scala" %% "jwt-core" % "9.4.5",
  "com.github.jwt-scala" %% "jwt-circe" % "9.4.5",
  
  // Validation
  "eu.timepit" %% "refined" % "0.11.0",
  
  // Date/Time
  "org.typelevel" %% "cats-time" % "0.5.1",
  
  // Utilities
  "org.apache.commons" % "commons-lang3" % "3.14.0",
  "commons-codec" % "commons-codec" % "1.16.0",
  
  // WebSockets
  "org.http4s" %% "http4s-jdk-http-client" % "0.9.1",
  
  // Redis
  "dev.profunktor" %% "redis4cats-effects" % "1.5.2",
  "dev.profunktor" %% "redis4cats-streams" % "1.5.2",
  
  // Metrics (optional)
  "io.dropwizard.metrics" % "metrics-core" % "4.2.23",
  
  // Testing
  "org.scalatest" %% "scalatest" % "3.2.17" % Test,
  "org.scalatestplus" %% "mockito-4-11" % "3.2.17.0" % Test,
  "org.http4s" %% "http4s-testing" % http4sVersion % Test,
  "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test,
  "com.h2database" % "h2" % "2.2.224" % Test,
  "org.testcontainers" % "testcontainers" % "1.19.3" % Test,
  "org.testcontainers" % "postgresql" % "1.19.3" % Test
)

// Compiler options
scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-feature",
  "-deprecation",
  "-unchecked",
  "-language:implicitConversions",
  "-language:higherKinds",
  "-language:existentials",
  "-language:postfixOps",
  "-Xlint",
  "-Ywarn-dead-code",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Wconf:cat=other-match-analysis:error"
)

// Assembly plugin for fat JAR
ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "application.conf" => MergeStrategy.concat
  case "reference.conf" => MergeStrategy.concat
  case x => MergeStrategy.first
}

// Docker plugin
enablePlugins(DockerPlugin, JavaAppPackaging)

dockerBaseImage := "openjdk:11-jre-slim"
dockerExposedPorts := Seq(8080, 9090)

Docker / packageName := "obp-trading"
Docker / version := version.value
Docker / dockerRepository := Some("openbankproject")

// Test options
Test / fork := true
Test / parallelExecution := false
Test / testOptions += Tests.Argument("-oDF")

// Run options
fork := true
javaOptions in run ++= Seq(
  "-Xmx2G",
  "-Xms512M",
  "-server",
  "-Dconfig.resource=application.conf"
)

// Development settings
ThisBuild / watchBeforeCommand := Watch.clearScreen
ThisBuild / watchTriggeredMessage := Watch.clearScreenOnTrigger
ThisBuild / watchForceTriggerOnAnyChange := true

// Publishing
publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { _ => false }

licenses := Seq("AGPL-3.0" -> url("https://www.gnu.org/licenses/agpl-3.0.html"))

homepage := Some(url("https://github.com/OpenBankProject/OBP-Trading"))

scmInfo := Some(
  ScmInfo(
    url("https://github.com/OpenBankProject/OBP-Trading"),
    "scm:git@github.com:OpenBankProject/OBP-Trading.git"
  )
)

developers := List(
  Developer(
    id = "obp-team",
    name = "Open Bank Project Team",
    email = "contact@openbankproject.com",
    url = url("https://openbankproject.com")
  )
)