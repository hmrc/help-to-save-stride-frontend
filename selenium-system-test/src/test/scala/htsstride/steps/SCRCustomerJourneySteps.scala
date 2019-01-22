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
import htsstride.utils.CustomerDetails.validCustomerDetails

class SCRCustomerJourneySteps extends Steps with NINOGenerator {

  Given("^the operator is logged in with SCR clearance$") {
    StrideSignInPage.authenticateSCROperator()
  }

  When("^the operator fills in the applicant's details$") {
    SCRCustomerEligiblePage.fillInSCRDetails(validCustomerDetails)
  }

  When("^the operator fills in the ineligible applicant's details$") {
    NotEligibleSCRPage.fillInSCRDetails(validCustomerDetails)
  }

  And("^verifies the applicant's details are correct$") {
    CheckCustomersDetailsPage.submitAndCheck()
  }

  When("^the operator enters the first name as null$") {
    SCRCustomerEligiblePage.fillInSCRDetails(validCustomerDetails.copy(firstName = None))
  }

  And("^the SCR applicant is eligible$") {
    CheckEligibilityPage.checkEligibility(generateEligibleNINO())
    Browser.checkCurrentPageIs(SCRCustomerEligiblePage)
  }

  And("the SCR applicant is ineligible$") {
    CheckEligibilityPage.checkEligibility(generateIneligibleNINO())
    Browser.checkCurrentPageIs(NotEligibleSCRPage)
  }

  And("the operator ticks the 'I confirm I have met these conditions' button") {
    NotEligibleSCRPage.checkTheConditionBox()
  }

  Then("^the enter a first name error message appears$") {
    Browser.checkCurrentPageIs(CustomerEligiblePage, true)
    CustomerEligiblePage.findErrorMessageList() shouldBe Some("Enter a first name")
    ()
  }

  When("^the operator enters a last name with over 300 characters$") {
    val surname = List.fill(301)("a").mkString("")
    SCRCustomerEligiblePage.fillInSCRDetails(validCustomerDetails.copy(lastName = Some(surname)))
  }

  Then("^the last name over the limit error message appears$") {
    Browser.checkCurrentPageIs(CustomerEligiblePage, true)
    CustomerEligiblePage.findErrorMessageList() shouldBe Some("Last name must be 300 characters or less")
    ()
  }

  When("^the operator enters a date of birth in the future$") {
    SCRCustomerEligiblePage.fillInSCRDetails(validCustomerDetails.copy(dobYear = Some("2100")))
  }

  Then("^the date of birth must be in the past error message appears$") {
    Browser.checkCurrentPageIs(CustomerEligiblePage, true)
    CustomerEligiblePage.findErrorMessageList() shouldBe Some("Date of birth must be in the past")
    ()
  }

  When("^the operator enters a month as 0 and the year as null$") {
    SCRCustomerEligiblePage.fillInSCRDetails(validCustomerDetails.copy(dobMonth = Some("0"), dobYear = None))
  }

  Then("^the enter a date of birth error message appears$") {
    Browser.checkCurrentPageIs(CustomerEligiblePage, true)
    CustomerEligiblePage.findErrorMessageList() shouldBe Some("Enter a date of birth and include a day, month and year")
    ()
  }

  When("^the operator enters a day that is greater than 31 and a year earlier than 1900$") {
    SCRCustomerEligiblePage.fillInSCRDetails(validCustomerDetails.copy(dobDay  = Some("644"), dobYear = Some("1897")))
  }

  Then("^the enter a real date of birth error message appears$") {
    Browser.checkCurrentPageIs(CustomerEligiblePage, true)
    CustomerEligiblePage.findErrorMessageList() shouldBe Some("Enter a real date of birth")
    ()
  }

  When("^the operator enters over 30 characters for the address line 1$") {
    val addressLineOne = List.fill(36)("a").mkString("")
    SCRCustomerEligiblePage.fillInSCRDetails(validCustomerDetails.copy(addressLine1 = Some(addressLineOne)))
  }

  Then("^the address line 1 error message appears$") {
    Browser.checkCurrentPageIs(CustomerEligiblePage, true)
    CustomerEligiblePage.findErrorMessageList() shouldBe Some("Address line 1 must be 35 characters or less")
    ()
  }

  When("^the operator enters null for the address line 2$") {
    SCRCustomerEligiblePage.fillInSCRDetails(validCustomerDetails.copy(addressLine2 = None))
  }

  Then("^the enter an address line 2 error message appears$") {
    Browser.checkCurrentPageIs(CustomerEligiblePage, true)
    CustomerEligiblePage.findErrorMessageList() shouldBe Some("Enter an address line 2")
    ()
  }

  When("^the operator enters over 10 characters for the postcode$") {
    val postcode = List.fill(11)("a").mkString("")
    SCRCustomerEligiblePage.fillInSCRDetails(validCustomerDetails.copy(postcode = Some(postcode)))
  }

  Then("^the enter a real postcode error message appears$") {
    Browser.checkCurrentPageIs(CustomerEligiblePage, true)
    CustomerEligiblePage.findErrorMessageList() shouldBe Some("Enter a real postcode")
    ()
  }

  When("^the operator enters invalid input$"){
    NotEligibleSCRPage.inputIncorrectDetails()
  }

  Then("^the appropriate error messages appear$") {
    Browser.checkCurrentPageIs(NotEligibleSCRPage)

    val errorListHtml: Option[String] = NotEligibleSCRPage.findErrorMessageList()

    List(
      "Enter a first name",
      "Last name must be 300 characters or less",
      "Date of birth must be in the past",
      "Address line 1 must be 35 characters or less",
      "Enter an address line 2",
      "Enter a real postcode"
    ).foreach(expectedErrorMessage ⇒
        withClue(s"For error '$expectedErrorMessage' "){
          assert(errorListHtml.exists(_.contains(expectedErrorMessage)) == true)
        })
    ()
  }
}
