import sbt.*

object AppDependencies {
  val playVersion = "play-30"
  val hmrcBootstrapVersion = "9.0.0"
  val hmrcMongoVersion = "1.7.0"

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-$playVersion"         % hmrcMongoVersion,
    "uk.gov.hmrc"       %% s"bootstrap-frontend-$playVersion" % hmrcBootstrapVersion,
    "uk.gov.hmrc"       %% s"domain-$playVersion"             % "9.0.0",
    "org.typelevel"     %% "cats-core"                        % "2.10.0",
    "uk.gov.hmrc"       %% s"play-frontend-hmrc-$playVersion" % "10.11.0"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "uk.gov.hmrc.mongo" %% s"hmrc-mongo-test-$playVersion" % hmrcMongoVersion     % scope,
    "org.scalatestplus" %% "scalacheck-1-17"               % "3.2.18.0"           % scope,
    "uk.gov.hmrc"       %% s"bootstrap-test-$playVersion"  % hmrcBootstrapVersion % scope,
    "org.mockito"       %% "mockito-scala"                 % "1.17.31"            % scope
  )
}
