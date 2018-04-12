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

import htsstride.pages._
import htsstride.utils.NINOGenerator

class DigitallyExcludedJourneySteps extends Steps with NINOGenerator {

  Given("^the operator is logged in$") {
    StrideSignInPage.authenticateOperator()
  }

  And("^an applicant is eligible$") {
    IntroductionHelpToSavePage.checkEligibility(generateEligibleNINO())
  }

  And("^an applicant with NINO (.*) is eligible") { (nino: String) ⇒
    IntroductionHelpToSavePage.checkEligibility(nino)
  }

  And("^the internal operator chooses to create an account on behalf of the applicant$") {
    CustomerEligiblePage.continue()
    CreateAccountPage.createAccount()
  }

  Then("^an account is successfully created$") {
    AccountCreatedPage.verifyPage()
  }

  When("^the internal operator is in the process of creating an account on behalf of the applicant$") {
    CustomerEligiblePage.continue()
  }

  And("^they cancel out of creating an account on the create account screen and choose to finish the call$") {
    CreateAccountPage.cancelCreateAccount()
  }

  And("^they cancel out of creating an account when asked to confirm the applicant's details and choose to finish the call$") {
    CustomerEligiblePage.cancelApplication()
  }

  And("^an applicant is NOT eligible$") {
  }

  When("^the internal operator does an eligibility check on behalf of the applicant$") {
    IntroductionHelpToSavePage.checkEligibility(generateIneligibleNINO())
  }

  Then("^they see that the applicant is NOT eligible$") {
    NotEligiblePage.verifyPage()
  }

  When("^they choose the finish the call$") {
    NotEligiblePage.finishCall()
  }

  Then("^they have the option to enter a new applicant's NINO on the opening screen$") {
    IntroductionHelpToSavePage.verifyPage()
  }

  When("^the eligibility service is down and an operator chooses to pass an applicant through the eligibility check$") {
    IntroductionHelpToSavePage.checkEligibility(generateEligibilityHTTPErrorCodeNINO(500))
  }

  Then("^they see a technical error$") {
    ErrorPage.verifyPage()
  }

  And("^there was a button to go back$") {
    ErrorPage.clickGoBackButton()
  }

  When("^the operator does an eligibility check for an existing account holder$") {
    IntroductionHelpToSavePage.checkEligibility(generateAccountCreatedNINO())
  }

  Then("^they see account already exists message$") {
    AccountAlreadyExistsPage.verifyPage()
  }
  Given("^the operator does an eligibility check when NS&I is down$") {
    IntroductionHelpToSavePage.checkEligibility(generateAccountCreationErrorNINO())
  }

  When("^the internal operator attempts to create an account on behalf of the applicant$") {
    CustomerEligiblePage.continue()
    CreateAccountPage.createAccount()
  }

}
