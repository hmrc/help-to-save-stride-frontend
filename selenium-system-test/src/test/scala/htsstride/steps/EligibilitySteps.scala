/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package htsstride.steps

import htsstride.browser.Browser
import htsstride.pages._
import htsstride.pages.eligibility._
import htsstride.utils.NINOGenerator

class EligibilitySteps extends Steps with NINOGenerator {

  Given("^the applicant has NINO (.*)$") { (nino: String) ⇒
    defineNINO(nino)
  }

  When("^the operator does an eligibility check for an existing account holder$") {
    IntroductionHelpToSavePage.checkEligibility(generateAccountCreatedNINO())
  }

  When("^the internal operator does an eligibility check on behalf of the applicant$") {
    IntroductionHelpToSavePage.checkEligibility(currentNINO())
  }

  Given("^an applicant is eligible$") {
    IntroductionHelpToSavePage.checkEligibility(generateEligibleNINO())
    Browser.checkCurrentPageIs(CustomerEligiblePage)
  }

  Given("^the operator does an eligibility check when NS&I is down$") {
    IntroductionHelpToSavePage.checkEligibility(generateAccountCreationErrorNINO())
  }

  When("^the eligibility service is down and an operator chooses to pass an applicant through the eligibility check$") {
    IntroductionHelpToSavePage.checkEligibility(generateEligibilityHTTPErrorCodeNINO(500))
  }

  Then("^they see that the applicant is NOT eligible for Help to Save with reason code (.+)$") { (reason: Int) ⇒
    reason match {
      case 3 ⇒
        Browser.checkCurrentPageIs(NotEligibleReason3Page)
        val notEligibleTextItems = NotEligibleReason3Page.notEligibleText
        notEligibleTextItems.foreach(text ⇒ Browser.isTextOnPage(text) shouldBe Right(())
        )
      case 5 ⇒
        Browser.checkCurrentPageIs(NotEligibleReason5Page)
        val notEligibleTextItems = NotEligibleReason5Page.notEligibleText
        notEligibleTextItems.foreach(text ⇒ Browser.isTextOnPage(text) shouldBe Right(())
        )
      case 4 | 9 ⇒
        Browser.checkCurrentPageIs(NotEligibleReason4And9Page)
        val notEligibleTextItems = NotEligibleReason4And9Page.notEligibleText
        notEligibleTextItems.foreach(text ⇒ Browser.isTextOnPage(text) shouldBe Right(())
        )
    }
  }
}
