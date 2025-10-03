val appName = "help-to-save-stride-frontend"

lazy val microservice = Project(appName, file("."))
  .settings(majorVersion := 2)
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(onLoadMessage := "")
  .settings(CodeCoverageSettings.settings *)
  .settings(scalaVersion := "3.7.1")
  .settings(PlayKeys.playDefaultPort := 7006)
  .settings(libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test())
  .settings(scalafmtOnCompile := true)
  .settings(
    scalacOptions ++= Seq(
      "-Wconf:src=routes/.*:s",
      "-Wconf:msg=unused&src=html/.*:s",
      "-Wconf:msg=Flag.*repeatedly:s",
      "-Wconf:src=html/.*:s"
    )
  )
  .settings(Test / testOptions -= Tests
    .Argument( "-u", "target/test-reports", "-h", "target/test-reports/html-report"))
  .settings(
    Test / testOptions += Tests.Argument(
      TestFrameworks.ScalaTest,
      "-u",
      "target/test-reports",
      "-h",
      "target/test-reports/html-report"))

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
