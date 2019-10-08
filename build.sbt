import com.typesafe.sbt.uglify.Import
import play.core.PlayVersion
import sbt.Keys.{libraryDependencies, _}
import sbt._
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning
import wartremover.{Wart, Warts, wartremoverErrors, wartremoverExcluded}

val test = "test"

val appName = "help-to-save-stride-frontend"

lazy val plugins: Seq[Plugins] = Seq.empty
lazy val playSettings: Seq[Setting[_]] = Seq.empty

lazy val dependencies = Seq(
  ws,
  "uk.gov.hmrc" %% "govuk-template" % "5.40.0-play-26",
  "uk.gov.hmrc" %% "mongo-caching" % "6.6.0-play-26",
  "uk.gov.hmrc" %% "play-ui" % "8.1.0-play-26",
  "uk.gov.hmrc" %% "bootstrap-play-26" % "1.0.0",
  "uk.gov.hmrc" %% "auth-client" % "2.29.0-play-26",
  "uk.gov.hmrc" %% "domain" % "5.6.0-play-26",
  "org.typelevel" %% "cats-core" % "1.5.0",
  "com.github.kxbmap" %% "configs" % "0.4.4"
)

lazy val testDependencies = Seq(
  "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-26" % test,
  "org.scalatest" %% "scalatest" % "3.0.8" % test,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % test,
  "com.typesafe.play" %% "play-ws" % PlayVersion.current % test,
  "org.scalamock" %% "scalamock-scalatest-support" % "3.6.0" % test,
  "uk.gov.hmrc" %% "stub-data-generator" % "0.5.3" % test,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.6" % test,
  "uk.gov.hmrc" %% "bootstrap-play-26" % "1.0.0" % test,
  "uk.gov.hmrc" %% "reactivemongo-test" % "4.15.0-play-26" % test
//  "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.3" % test,
// below for selenium tests

)

lazy val formatMessageQuotes = taskKey[Unit]("Makes sure smart quotes are used in all messages")

def seleniumTestFilter(name: String): Boolean = name.contains("suites")

def unitTestFilter(name: String): Boolean = !seleniumTestFilter(name)

lazy val SeleniumTest = config("selenium") extend Test

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*config.*;.*(AuthService|BuildInfo|Routes|JsErrorOps).*;.*views.html.*",
    ScoverageKeys.coverageMinimum := 88,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

lazy val scalariformSettings = {
  import com.typesafe.sbt.SbtScalariform.ScalariformKeys
  import scalariform.formatter.preferences._
  // description of options found here -> https://github.com/scala-ide/scalariform
  ScalariformKeys.preferences := ScalariformKeys.preferences.value
    .setPreference(AlignArguments, true)
    .setPreference(AlignParameters, true)
    .setPreference(AlignSingleLineCaseStatements, true)
    .setPreference(CompactControlReadability, false)
    .setPreference(CompactStringConcatenation, false)
    .setPreference(DanglingCloseParenthesis, Preserve)
    .setPreference(DoubleIndentConstructorArguments, true)
    .setPreference(DoubleIndentMethodDeclaration, true)
    .setPreference(FirstArgumentOnNewline, Preserve)
    .setPreference(FirstParameterOnNewline, Preserve)
    .setPreference(FormatXml, true)
    .setPreference(IndentLocalDefs, true)
    .setPreference(IndentPackageBlocks, true)
    .setPreference(IndentSpaces, 2)
    .setPreference(IndentWithTabs, false)
    .setPreference(MultilineScaladocCommentsStartOnFirstLine, false)
    .setPreference(NewlineAtEndOfFile, true)
    .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, false)
    .setPreference(PreserveSpaceBeforeArguments, true)
    .setPreference(RewriteArrowSymbols, true)
    .setPreference(SpaceBeforeColon, false)
    .setPreference(SpaceBeforeContextColon, false)
    .setPreference(SpaceInsideBrackets, false)
    .setPreference(SpaceInsideParentheses, false)
    .setPreference(SpacesAroundMultiImports, false)
    .setPreference(SpacesWithinPatternBinders, true)
}

lazy val wartRemoverSettings = {
  // list of warts here: http://www.wartremover.org/doc/warts.html
  val excludedWarts = Seq(
    Wart.DefaultArguments,
    Wart.FinalCaseClass,
    Wart.FinalVal,
    Wart.ImplicitConversion,
    Wart.ImplicitParameter,
    Wart.LeakingSealed,
    Wart.Nothing,
    Wart.Overloading,
    Wart.ToString,
    Wart.Var)

  wartremoverErrors in (Compile, compile) ++= Warts.allBut(excludedWarts: _*)
}

lazy val commonSettings = Seq(
  addCompilerPlugin("org.psywerx.hairyfotr" %% "linter" % "0.1.17"),
  majorVersion := 2,
  evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
  resolvers ++= Seq(
    Resolver.bintrayRepo("hmrc", "releases"),
    Resolver.jcenterRepo,
    "emueller-bintray" at "http://dl.bintray.com/emueller/maven" // for play json schema validator
  ),
  scalacOptions ++= Seq("-Xcheckinit", "-feature")
) ++ scalaSettings ++ publishingSettings ++ defaultSettings() ++ scalariformSettings ++ scoverageSettings ++ playSettings


lazy val microservice = Project(appName, file("."))
  .settings(commonSettings: _*)
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory, SbtWeb) ++ plugins: _*)
  .settings(playSettings ++ scoverageSettings: _*)
  .settings(scalaSettings: _*)
  .settings(publishingSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(PlayKeys.playDefaultPort := 7006)
  .settings(scalariformSettings: _*)
  .settings(wartRemoverSettings)
  // disable some wart remover checks in tests - (Any, Null, PublicInference) seems to struggle with
  // scalamock, (Equals) seems to struggle with stub generator AutoGen and (NonUnitStatements) is
  // imcompatible with a lot of WordSpec
  .settings(wartremoverErrors in (Test, compile) --= Seq(Wart.Any, Wart.Equals, Wart.Null, Wart.NonUnitStatements, Wart.PublicInference))
  .settings(wartremoverExcluded ++=
    routes.in(Compile).value ++
      (baseDirectory.value ** "*.sc").get ++
      Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala")
  )
  .settings(
    libraryDependencies ++= dependencies ++ testDependencies
  )
  .settings(
    retrieveManaged := true
  )
  .settings(resolvers ++= Seq(
    "hmrc-releases" at "https://artefacts.tax.service.gov.uk/artifactory/hmrc-releases/"
  ))
  .settings(
    // concatenate js
    Concat.groups := Seq(
      "javascripts/h2s-app.js" -> group(Seq("javascripts/extendPreventDoubleClick.js", "javascripts/overrideIneligibleCheckboxButtonBind.js", "javascripts/hts.js"))
    ),
    // prevent removal of unused code which generates warning errors due to use of third-party libs
    Import.uglifyCompressOptions := Seq("unused=false", "dead_code=false"),
    pipelineStages := Seq(digest),
    // below line required to force asset pipeline to operate in dev rather than only prod
    pipelineStages in Assets := Seq(concat,uglify),
    // only compress files generated by concat
    includeFilter in uglify := GlobFilter("h2s-*.js")
  )
.settings(
  formatMessageQuotes := {
    import sys.process._
    val result = (List("sed", "-i", s"""s/&rsquo;\\|''/’/g""", s"${baseDirectory.value.getAbsolutePath}/conf/messages") !)
    if(result != 0){ logger.log(Level.Warn, "WARNING: could not replace quotes with smart quotes") }
  },
  compile := ((compile in Compile) dependsOn formatMessageQuotes).value
)

lazy val selenium = (project in file("selenium-system-test"))
  .dependsOn(microservice)
  .configs(SeleniumTest)
  .settings(commonSettings: _*)
  .settings(wartRemoverSettings: _*)
  .enablePlugins(Seq(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins: _*)
  .settings(
    inConfig(SeleniumTest)(Defaults.testTasks),
    Keys.fork in SeleniumTest := true,
    unmanagedSourceDirectories in Test += baseDirectory.value / "selenium-system-test/src/test/scala",
    unmanagedResourceDirectories in Test += baseDirectory.value / "selenium-system-test/src/test/resources",
    testOptions in Test := Seq(Tests.Filter(unitTestFilter)),
    testOptions in SeleniumTest := Seq(Tests.Filter(seleniumTestFilter)),
    testOptions in SeleniumTest += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports/html-report"),
    testOptions in SeleniumTest += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports"),
    testOptions in SeleniumTest += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
  )
  .settings(
    libraryDependencies ++= Seq(
      "io.cucumber"           %% "cucumber-scala"         % "4.7.1" % test,
      "io.cucumber"           %  "cucumber-junit"         % "4.7.1" % test,
      "io.cucumber"           % "cucumber-picocontainer"  % "4.7.1" % test,
      "uk.gov.hmrc"           %% "webdriver-factory"      % "0.7.0" % test exclude( "org.slf4j","slf4j-simple")
    )
  )
  .settings(
    Keys.fork in Test := true,
    scalaSource in Test := baseDirectory.value / "src" / "test",
    resourceDirectory in Test := baseDirectory.value / "src" / "test" / "resources",
    testOptions in Test := Seq(Tests.Filter(name ⇒  name.contains("suites"))),
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-h", "target/test-reports/html-report"),
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-u", "target/test-reports"),
    testOptions in Test += Tests.Argument(TestFrameworks.ScalaTest, "-oDF")
  )

