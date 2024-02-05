import play.sbt.PlayImport.*
import sbt.*
import sbt.Keys.*

val appName = "help-to-save-stride-frontend"

lazy val microservice = Project(appName, file("."))
  .settings(majorVersion := 2)
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(CodeCoverageSettings.settings *)
  .settings(scalaVersion := "2.13.8")
  .settings(PlayKeys.playDefaultPort := 7006)
  .settings(libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test())
  .settings(scalafmtOnCompile := true)
  .settings(scalacOptions += "-Wconf:cat=unused-imports&src=html/.*:s")
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
