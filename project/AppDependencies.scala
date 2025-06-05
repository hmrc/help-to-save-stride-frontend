import sbt.*

object AppDependencies {
  val playVersion = "play-30"
  val hmrcBootstrapVersion = "9.12.0"
  val hmrcMongoVersion = "2.6.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVersion"         % hmrcMongoVersion,
    "uk.gov.hmrc"       %% s"bootstrap-frontend-$playVersion" % hmrcBootstrapVersion,
    "uk.gov.hmrc"       %% s"domain-$playVersion"             % "10.0.0",
    "org.typelevel"     %% "cats-core"                        % "2.12.0",
    "uk.gov.hmrc"       %% s"play-frontend-hmrc-$playVersion" % "12.1.0"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test-$playVersion"  % hmrcMongoVersion     % scope,
    "org.scalatestplus"   % "scalacheck-1-18_3"             % "3.2.19.0"       % scope,
    "org.scalamock"      %% "scalamock"                     % "6.0.0"          % scope,
    "uk.gov.hmrc"        %% s"bootstrap-test-$playVersion"  % hmrcBootstrapVersion % scope exclude ("org.playframework", "play-json_2.13")
  )
}
