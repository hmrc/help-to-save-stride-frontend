
package htsstride.pages.eligibility

object NotEligibleReason4And9Page extends NotEligiblePage {

  override val notEligibleText =
    List("Customer is not eligible for a Help to Save account.",
      "To be eligible for an account one of these must apply. The customer must be:",
      "entitled to Working Tax Credit and also receiving payments for Working Tax Credit or Child Tax Credit",
      "claiming Universal Credit and their household income must have met or exceeded the threshold in their last monthly assessment period",
      "Payments from Universal Credit are not considered to be income.",
      "If they have recently applied for Working Tax Credit or Universal Credit they may be eligible for an account, but will need to wait and apply again later.")
}