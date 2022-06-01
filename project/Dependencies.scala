import sbt._

object Dependencies {

  object versions {
    val http4s     = "0.23.12"
    val circe      = "0.14.2"
    val scalacache = "1.0.0-M6" // there is no stable version for Scala 3 yet
  }

  val mainDependencies = Seq(
    "org.typelevel"              %% "cats-core"           % "2.7.0",
    "org.typelevel"              %% "cats-effect"         % "3.3.12",
    "org.http4s"                 %% "http4s-dsl"          % versions.http4s,
    "org.http4s"                 %% "http4s-ember-server" % versions.http4s,
    "org.http4s"                 %% "http4s-ember-client" % versions.http4s,
    "org.http4s"                 %% "http4s-circe"        % versions.http4s,
    "io.circe"                   %% "circe-core"          % versions.circe,
    "io.circe"                   %% "circe-generic"       % versions.circe,
    "io.circe"                   %% "circe-literal"       % versions.circe,
    "ch.qos.logback"              % "logback-classic"     % "1.2.10",
    "com.typesafe.scala-logging" %% "scala-logging"       % "3.9.4",
    "com.github.cb372"           %% "scalacache-core"     % versions.scalacache,
    "com.github.cb372"           %% "scalacache-caffeine" % versions.scalacache
  )

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest"                     % "3.2.12",
    "org.typelevel" %% "cats-effect-testing-scalatest" % "1.4.0"
  ).map(_ % Test)

  val allDependencies = mainDependencies ++ testDependencies
}
