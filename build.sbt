import com.typesafe.sbt.uglify.Import
import play.core.PlayVersion
import sbt.Keys.{libraryDependencies, resolvers, _}
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import wartremover.Warts
import wartremover.WartRemover.autoImport.{wartremoverExcluded, wartremoverErrors}
import scala.language.postfixOps

val test = "test"
val appName = "help-to-save-stride-frontend"

lazy val plugins: Seq[Plugins] = Seq.empty
lazy val playSettings: Seq[Setting[_]] = Seq.empty
val playVersion = "play-28"
val hmrcBootstrapVersion = "7.15.0"

lazy val dependencies = Seq(
  ws,
  "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28" % "0.73.0",
  "uk.gov.hmrc" %% "bootstrap-frontend-play-28" % hmrcBootstrapVersion,
  "uk.gov.hmrc" %% "domain" % "8.1.0-play-28",
  "org.typelevel" %% "cats-core" % "2.8.0",
  "com.github.kxbmap" %% "configs" % "0.6.1",
  "uk.gov.hmrc" %% "play-frontend-hmrc" % s"7.23.0-$playVersion"
)

lazy val testDependencies = Seq(
  "com.vladsch.flexmark" % "flexmark-all"  % "0.35.10" % test,
  "uk.gov.hmrc.mongo" %% "hmrc-mongo-test-play-28" % "0.73.0" % test,
  "org.scalatest" %% "scalatest" % "3.2.9" % test,
  "org.scalamock" %% "scalamock" % "5.2.0" % test,
  "org.scalatestplus" %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % test,
  "com.typesafe.play" %% "play-test" % PlayVersion.current % test,
  "com.typesafe.play" %% "play-ws" % PlayVersion.current % test,
  "uk.gov.hmrc" %% "bootstrap-test-play-28" % hmrcBootstrapVersion % "test"
)

lazy val formatMessageQuotes = taskKey[Unit]("Makes sure smart quotes are used in all messages")

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    // Semicolon-separated list of regexs matching classes to exclude
    ScoverageKeys.coverageExcludedPackages := "<empty>;.*Reverse.*;.*config.*;.*(AuthService|BuildInfo|Routes|JsErrorOps).*;.*views.html.*",
    ScoverageKeys.coverageMinimumStmtTotal := 94,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    Test /parallelExecution := false
  )
}

lazy val scalariformSettings = {
  import com.typesafe.sbt.SbtScalariform.ScalariformKeys
  import scalariform.formatter.preferences._

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
    .setPreference(RewriteArrowSymbols, false)
    .setPreference(SpaceBeforeColon, false)
    .setPreference(SpaceBeforeContextColon, false)
    .setPreference(SpaceInsideBrackets, false)
    .setPreference(SpaceInsideParentheses, false)
    .setPreference(SpacesAroundMultiImports, false)
    .setPreference(SpacesWithinPatternBinders, true)
}

lazy val wartRemoverSettings = {
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
    Wart.Var,
    Wart.PlatformDefault,
    Wart.Any,
    Wart.StringPlusAny,
    Wart.Throw,
    Wart.TripleQuestionMark,
  )

  Seq(Compile / compile / wartremoverErrors ++= Warts.allBut(excludedWarts: _*),
    Test / compile / wartremoverErrors --= Seq(Wart.Any, Wart.Equals, Wart.Null, Wart.NonUnitStatements, Wart.PublicInference),
    wartremoverExcluded ++=
      (Compile / routes).value ++
        (baseDirectory.value ** "*.sc").get ++
        Seq(sourceManaged.value / "main" / "sbt-buildinfo" / "BuildInfo.scala")
  )
}

lazy val commonSettings = Seq(
  majorVersion := 2,
  update / evictionWarningOptions := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
  resolvers ++= Seq(
    Resolver.jcenterRepo,
    "emueller-bintray" at "https://dl.bintray.com/emueller/maven", // for play json schema validator,
    "HMRC-open-artefacts-maven2" at "https://open.artefacts.tax.service.gov.uk/maven2"
  ),
  scalacOptions ++= Seq("-Xcheckinit", "-feature"),
  Compile / scalacOptions -= "utf8"
) ++ scalaSettings ++ defaultSettings() ++ scalariformSettings ++ scoverageSettings ++ playSettings


lazy val microservice = Project(appName, file("."))
  .settings(commonSettings: _*)
  .enablePlugins(Seq(play.sbt.PlayScala, SbtDistributablesPlugin, SbtWeb) ++ plugins: _*)
  .settings(playSettings ++ scoverageSettings: _*)
  .settings(scalaSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(scalaVersion := "2.13.8")
  .settings(PlayKeys.playDefaultPort := 7006)
  .settings(scalariformSettings: _*)
  .settings(wartRemoverSettings)
  // disable some wart remover checks in tests - (Any, Null, PublicInference) seems to struggle with
  // scalamock, (Equals) seems to struggle with stub generator AutoGen and (NonUnitStatements) is
  // imcompatible with a lot of WordSpec
  .settings(libraryDependencies ++= dependencies ++ testDependencies)
  .settings(retrieveManaged := true)
  .settings(
    formatMessageQuotes := {
      import sys.process._
      val result = (List("sed", "-i", s"""s/&rsquo;\\|''/’/g""", s"${baseDirectory.value.getAbsolutePath}/conf/messages") !)
      if (result != 0) logger.log(Level.Warn, "WARNING: could not replace quotes with smart quotes")
    },
    compile := ((Compile / compile) dependsOn formatMessageQuotes).value
  )
  .settings(scalacOptions += "-Wconf:cat=unused-imports&src=html/.*:s")
  .settings(scalacOptions += "-Wconf:src=routes/.*:s")
  .settings(Global / lintUnusedKeysOnLoad := false)

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
