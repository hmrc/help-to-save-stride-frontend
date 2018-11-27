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
import htsstride.pages.{AccountAlreadyExistsPage, AccountCreatedPage, CheckEligibilityPage, CreateAccountPage}
import htsstride.pages.eligibility.CustomerEligiblePage
import htsstride.utils.NINOGenerator

class SmartQuotesSteps extends Steps with NINOGenerator {

  When("^the operator navigates to the /check-eligibility page$") { () ⇒
    CheckEligibilityPage.navigate()
    Browser.checkCurrentPageIs(CheckEligibilityPage)
  }

  When("^the operator navigates to the /customer-eligible page$") { () ⇒
    val nino = generateEligibleNINO()

    CheckEligibilityPage.navigate()
    CheckEligibilityPage.checkEligibility(nino)

    Browser.checkCurrentPageIs(CustomerEligiblePage)
  }

  When("^the operator navigates to the /account-created page$") { () ⇒
    val nino = generateEligibleNINO()

    CheckEligibilityPage.navigate()
    CheckEligibilityPage.checkEligibility(nino)

    Browser.checkCurrentPageIs(CustomerEligiblePage)
    CustomerEligiblePage.continue()

    Browser.checkCurrentPageIs(CreateAccountPage)
    CreateAccountPage.createAccount()

    Browser.checkCurrentPageIs(AccountCreatedPage)
  }

  When("^the operator navigates to the /customer-already-has-account page$") { () ⇒
    val nino = generateAccountCreatedNINO()

    CheckEligibilityPage.navigate()
    CheckEligibilityPage.checkEligibility(nino)

    Browser.checkCurrentPageIs(AccountAlreadyExistsPage)
  }

  Then("^they see that the page has only smart quotes$"){ () ⇒
    Browser.checkForOldQuotes()
  }

}
