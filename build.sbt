import play.sbt.PlayImport.*
import sbt.*
import sbt.Keys.*

val appName = "help-to-save-stride-frontend"

lazy val microservice = Project(appName, file("."))
  .settings(majorVersion := 2)
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .settings(onLoadMessage := "")
  .settings(CodeCoverageSettings.settings *)
  .settings(scalaVersion := "2.13.12")
  .settings(PlayKeys.playDefaultPort := 7006)
  .settings(libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test())
  .settings(scalafmtOnCompile := true)
  .settings(scalacOptions += "-Wconf:cat=unused-imports&src=html/.*:s")
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
  // Disable default sbt Test options (might change with new versions of bootstrap)
  .settings(Test / testOptions -= Tests
    .Argument("-o", "-u", "target/test-reports", "-h", "target/test-reports/html-report"))
  // Suppress successful events in Scalatest in standard output (-o)
  // Options described here: https://www.scalatest.org/user_guide/using_scalatest_with_sbt
  .settings(
    Test / testOptions += Tests.Argument(
      TestFrameworks.ScalaTest,
      "-oNCHPQR",
      "-u",
      "target/test-reports",
      "-h",
      "target/test-reports/html-report"))

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
