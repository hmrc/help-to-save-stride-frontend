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
import htsstride.pages._
import htsstride.pages.eligibility._
import htsstride.utils.NINOGenerator

class DigitallyExcludedJourneySteps extends Steps with NINOGenerator {

  Given("^the operator is logged in$") {
    StrideSignInPage.authenticateOperator()
  }

  When("^the internal operator chooses to create an account on behalf of the applicant$") {
    CustomerEligiblePage.continue()
    Browser.checkCurrentPageIs(CreateAccountPage)
    Browser.checkForOldQuotes()
    CreateAccountPage.createAccount()
  }

  Then("^an account is successfully created$") {
    Browser.checkCurrentPageIs(AccountCreatedPage)
    Browser.checkForOldQuotes()
  }

  When("^the internal operator is in the process of creating an account on behalf of the applicant$") {
    CustomerEligiblePage.continue()
  }

  When("^they cancel out of creating an account on the create account screen and choose to finish the call$") {
    CreateAccountPage.cancelCreateAccount()
  }

  When("^they cancel out of creating an account when asked to confirm the applicant's details and choose to finish the call$") {
    CustomerEligiblePage.cancelApplication()
  }

  When("^they choose to finish the call$") {
    NotEligibleReason3Page.finishCall()
  }

  Then("^they have the option to enter a new applicant's NINO on the opening screen$") {
    Browser.checkCurrentPageIs(CheckEligibilityPage)
  }

  Then("they see the application has been cancelled$") {
    Browser.checkCurrentPageIs(ApplicationCancelledPage)
  }

  Then("^they see a technical error$") {
    Browser.checkCurrentPageIs(ErrorPage)
  }

  And("^there was a button to go back$") {
    ErrorPage.clickGoBackButton()
  }

  Then("^they see account already exists message$") {
    Browser.checkCurrentPageIs(AccountAlreadyExistsPage)
  }

  When("^the internal operator attempts to create an account on behalf of the applicant$") {
    CustomerEligiblePage.continue()
    CreateAccountPage.createAccount()
  }
}
