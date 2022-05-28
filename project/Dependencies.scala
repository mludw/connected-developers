import sbt._

object Dependencies {
  val mainDependencies = Seq(
    "org.typelevel" %% "cats-core"   % "2.7.0",
    "org.typelevel" %% "cats-effect" % "3.3.12"
  )

  val testDependencies = Seq(
    "org.scalatest" %% "scalatest" % "3.2.12"
  ).map(_ % Test)

  val allDependencies = mainDependencies ++ testDependencies
}
