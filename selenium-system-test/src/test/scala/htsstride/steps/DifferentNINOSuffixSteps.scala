/*
 * Copyright 2019 HM Revenue & Customs
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
import htsstride.pages.{CheckEligibilityPage, CreateAccountPage}
import htsstride.pages.eligibility.CustomerEligiblePage
import htsstride.utils.NINOGenerator

class DifferentNINOSuffixSteps extends Steps with NINOGenerator {

  val ninoForSuffixTest = generateEligibleNINO()
  val ninoWithSuffixC = ninoForSuffixTest.take(8).toString + "C"
  val ninoWithSuffixD = ninoWithSuffixC.take(8).toString + "D"

  And("^the operator creates an account on behalf of the user with NINO suffix C$") { () ⇒
    CheckEligibilityPage.checkEligibility(ninoWithSuffixC)
    Browser.checkCurrentPageIs(CustomerEligiblePage)
    CustomerEligiblePage.continue()
    Browser.checkCurrentPageIs(CreateAccountPage)
    CreateAccountPage.createAccount()
  }

  When("^the operator attempts to create an account on behalf of the user with NINO suffix D$") { () ⇒
    CheckEligibilityPage.checkEligibility(ninoWithSuffixD)
  }
}
