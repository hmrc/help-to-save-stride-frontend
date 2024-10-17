import sbt.Setting
import scoverage.ScoverageKeys

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq(
    "<empty>",
    "Reverse.*",
    ".*config.*",
    ".*views.html.*",
    ".*(AuthService|BuildInfo|Routes|JsErrorOps).*"
  )

  val settings: Seq[Setting[?]] =   Seq(
    ScoverageKeys.coverageExcludedPackages := excludedPackages.mkString(";"),
    ScoverageKeys.coverageMinimumStmtTotal := 94,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true
  )
}
