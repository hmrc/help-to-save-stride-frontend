import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {
  val playVersion = "play-30"
  val hmrcBootstrapVersion = "8.6.0"
  val hmrcMongoVersion = "1.7.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVersion"         % hmrcMongoVersion,
    "uk.gov.hmrc"       %% s"bootstrap-frontend-$playVersion" % hmrcBootstrapVersion,
    "uk.gov.hmrc"       %% s"domain-$playVersion"             % "9.0.0",
    "org.typelevel"     %% "cats-core"                        % "2.10.0",
    "uk.gov.hmrc"       %% s"play-frontend-hmrc-$playVersion" % "10.11.0"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo"     %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion     % scope,
    "org.scalamock"         %% "scalamock"                     % "5.2.0"              % scope,
    "org.scalatestplus"     %% "scalatestplus-scalacheck"      % "3.1.0.0-RC2"        % scope,
    "uk.gov.hmrc"           %% s"bootstrap-test-$playVersion"  % hmrcBootstrapVersion % scope,
    "org.mockito"           %% "mockito-scala"                 % "1.17.31"            % scope,
    "com.github.tomakehurst" % "wiremock"                      % "3.0.0-beta-7"       % scope
  )
}
