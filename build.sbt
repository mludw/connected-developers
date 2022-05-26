val scala3Version = "3.1.2"

scalacOptions ++= Seq(
  "-deprecation",         // emit warning and location for usages of deprecated APIs
  "-explain",             // explain errors in more detail
  "-explain-types",       // explain type errors in more detail
  "-feature",             // emit warning and location for usages of features that should be imported explicitly
  "-print-lines",         // show source code line numbers.
  "-Xfatal-warnings",     // fail the compilation if there are any warnings
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "connected-developers",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.12" % Test

  )
