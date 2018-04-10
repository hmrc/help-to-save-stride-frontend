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

import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.UserInfo
import uk.gov.hmrc.helptosavestridefrontend.models.NSIUserInfo.ContactDetails
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavestridefrontend.models.{Address, NSIUserInfo, Name, PayePersonalDetails}

trait TestData { // scalastyle:off magic.number

  val ppDetails = PayePersonalDetails(
    Name("A", "Smith"),
    LocalDate.parse("1980-01-01"),
    Address("1 Station Road", "Town Centre", Some("Sometown"), Some("Anyshire"), Some("UK"), "AB12 3CD", Some("1")), Some("07841097845"))

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
              "countryCode": "1"
            },
            "phoneNumber": "07841097845"
     }""".stripMargin

  val contactDetails = ContactDetails("1 Station Road", "Town Centre", Some("Sometown"), Some("Anyshire"), Some("County"), "AB12 3CD", Some("1"), Some("07841097845"), "00")

  val nsiUserInfo = NSIUserInfo("A", "Smith", LocalDate.parse("1980-01-01"), "AE123456C", contactDetails, "callCentre")

  val nsiUserInfoJson: String =
    """{
      | "forename":"A",
      | "surname":"Smith",
      | "dateOfBirth":"1980-01-01",
      | "nino":"AE123456C",
      | "contactDetails": {
      |   "address1":"1 Station Road",
      |   "address2":"Town Centre",
      |   "address3":"Sometown",
      |   "address4":"Anyshire",
      |   "address5":"County",
      |   "countryCode": 1,
      |   "postcode":"AB12 3CD",
      |   "phoneNUmber":"07841097845",
      |   "communicationPreference":"00"
      | },
      | "registrationChannel":"callCentre"
      |
    }""".stripMargin

  val eligibleResponse = Eligible(EligibilityCheckResponse("eligible", 1, "Tax credits", 1))

  val ineligibleResponse = Ineligible(EligibilityCheckResponse("ineligible", 2, "Tax credits", 3))

  val accountExistsResponse = AlreadyHasAccount(EligibilityCheckResponse("eligible", 3, "Tax credits", 7))

  val cacheKey = UUID.randomUUID().toString

  val eligibleStrideUserInfo = UserInfo.EligibleWithNSIUserInfo(eligibleResponse.value, nsiUserInfo)

  val ineligibleStrideUserInfo = UserInfo.Ineligible(ineligibleResponse.value)

  val accountExistsStrideUserInfo = UserInfo.AlreadyHasAccount
}
