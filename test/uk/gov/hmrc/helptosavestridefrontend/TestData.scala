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

package uk.gov.hmrc.helptosavestridefrontend

import java.time.LocalDate
import java.util.UUID

import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.SessionEligibilityCheckResult
import uk.gov.hmrc.helptosavestridefrontend.models.NSIUserInfo
import uk.gov.hmrc.helptosavestridefrontend.models.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}

trait TestData { // scalastyle:off magic.number

  val payeDetailsJson: String =
    """{
            "name": {
              "firstForenameOrInitial": "A",
              "surname": "Smith"
            },
            "dateOfBirth": "1980-01-01",
            "address": {
              "line1": "1 Station Road",
              "line2": "Town Centre",
              "line3": "Sometown",
              "line4": "Anyshire",
              "line5": "County",
              "postcode": "AB12 3CD",
              "countryCode": "GB"
            },
            "phoneNumber": "07841097845"
     }""".stripMargin

  val contactDetails = ContactDetails("1 Station Road", "Town Centre", Some("Sometown"), Some("Anyshire"), Some("County"), "AB12 3CD", Some("GB"), Some("07841097845"), "00")

  val nsiUserInfo = NSIUserInfo("A", "Smith", LocalDate.parse("1980-01-01"), "AE123456C", contactDetails, "callCentre")

  val eligibleResponse = Eligible(EligibilityCheckResponse("eligible", 1, "Tax credits", 1))

  val ineligibleResponse = Ineligible(EligibilityCheckResponse("ineligible", 2, "Tax credits", 3))

  val accountExistsResponse = AlreadyHasAccount(EligibilityCheckResponse("eligible", 3, "Tax credits", 7))

  val cacheKey = UUID.randomUUID().toString

  val eligibleStrideUserInfo = SessionEligibilityCheckResult.Eligible(eligibleResponse.value)

  val ineligibleStrideUserInfo = SessionEligibilityCheckResult.Ineligible(ineligibleResponse.value, false)

  val ineligibleManualOverrideStrideUserInfo = SessionEligibilityCheckResult.Ineligible(ineligibleResponse.value, true)

  val accountExistsStrideUserInfo = SessionEligibilityCheckResult.AlreadyHasAccount
}
