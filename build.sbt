val scala3Version = "3.1.2"

scalacOptions ++= CompilerOptions.scalacOptions

lazy val root = project
  .in(file("."))
  .settings(
    name         := "connected-developers",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Dependencies.allDependencies
  )
