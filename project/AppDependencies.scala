import play.core.PlayVersion
import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {
  val playVersion = "play-28"
  val hmrcBootstrapVersion = "7.23.0"
  val hmrcMongoVersion = "0.73.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"         % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "bootstrap-frontend-play-28" % hmrcBootstrapVersion,
    "uk.gov.hmrc"       %% "domain"                     % "8.1.0-play-28",
    "org.typelevel"     %% "cats-core"                  % "2.9.0",
    "uk.gov.hmrc"       %% "play-frontend-hmrc"         % s"7.29.0-$playVersion"
  )

  def test(scope: String = "test"): Seq[ModuleID] = Seq(
    "com.vladsch.flexmark" % "flexmark-all"              % "0.35.10"            % scope,
    "uk.gov.hmrc.mongo"    %% "hmrc-mongo-test-play-28"  % hmrcMongoVersion     % scope,
    "org.scalatest"        %% "scalatest"                % "3.2.9"              % scope,
    "org.scalamock"        %% "scalamock"                % "5.2.0"              % scope,
    "org.scalatestplus"    %% "scalatestplus-scalacheck" % "3.1.0.0-RC2"        % scope,
    "com.typesafe.play"    %% "play-test"                % PlayVersion.current  % scope,
    "com.typesafe.play"    %% "play-ws"                  % PlayVersion.current  % scope,
    "uk.gov.hmrc"          %% "bootstrap-test-play-28"   % hmrcBootstrapVersion % scope
  )
}
