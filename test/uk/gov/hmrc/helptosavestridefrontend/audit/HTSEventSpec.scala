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

package uk.gov.hmrc.helptosavestridefrontend.audit

import java.time.LocalDate

import play.api.libs.json.Json
import uk.gov.hmrc.helptosavestridefrontend.TestSupport
import uk.gov.hmrc.helptosavestridefrontend.models.{OperatorDetails, PersonalInformationDisplayed}

class HTSEventSpec extends TestSupport {

  val appName = "help-to-save-stride-frontend"

  val operatorDetails = OperatorDetails(List("hts helpdesk advisor"), "pid", "name", "test@operator.com")

  "PersonalInformationDisplayedToOperator" must {

    "be created with appropriate fields" in {

      val event = PersonalInformationDisplayedToOperator(
        PersonalInformationDisplayed("AE123456C", "foo bar", Some(LocalDate.of(1900, 1, 1)), List("address1", "address2")),
        operatorDetails,
        "path"
      )
      event.value.auditSource shouldBe appName
      event.value.auditType shouldBe "personalInformationDisplayedToOperator"
      event.value.tags.get("path") shouldBe Some("path")
      event.value.tags.get("transactionName") shouldBe Some("personal-information-displayed-to-stride-operator")
      event.value.detail shouldBe
        Json.parse(
          """{
            |   "detailsDisplayed":{
            |      "nino":"AE123456C",
            |      "name":"foo bar",
            |      "dateOfBirth":"1900-01-01",
            |      "address": [ "address1", "address2" ]
            |   },
            |   "operatorDetails":{
            |      "roles":[
            |         "hts helpdesk advisor"
            |      ],
            |      "pid":"pid",
            |      "name":"name",
            |      "email":"test@operator.com"
            |   }
            |}""".stripMargin
        )
    }
  }

  "ManualAccountCreationSelected" must {

    "be created with appropriate fields" in {

      val event = ManualAccountCreationSelected("nino", "path", operatorDetails)
      event.value.auditSource shouldBe appName
      event.value.auditType shouldBe "manualAccountCreationSelected"
      event.value.tags.get("path") shouldBe Some("path")
      event.value.tags.get("transactionName") shouldBe Some("manual-account-created")
      event.value.detail shouldBe
        Json.parse(
          """{
            |   "nino": "nino",
            |   "operatorDetails":{
            |      "roles":[
            |         "hts helpdesk advisor"
            |      ],
            |      "pid":"pid",
            |      "name":"name",
            |      "email":"test@operator.com"
            |   }
            |}""".stripMargin
        )
    }
  }
}
