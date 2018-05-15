
package htsstride.pages.eligibility

import org.openqa.selenium.WebDriver

object NotEligibleReason3Page extends NotEligiblePage {

  override val notEligibleText =
    List("Customer is not eligible for a Help to Save account.",
      "They are eligible for Working Tax Credit but they are not getting payments for either:",
      "Working Tax Credit",
      "Child Tax Credit")

  def finishCall()(implicit driver: WebDriver): Unit =
    clickEndCall()
}