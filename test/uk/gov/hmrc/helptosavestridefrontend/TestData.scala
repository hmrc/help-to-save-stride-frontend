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

import uk.gov.hmrc.helptosavestridefrontend.models.{Address, Name, PayePersonalDetails}

trait TestData {

  val ppDetails = PayePersonalDetails(
    Name("A", "Smith"),
    LocalDate.parse("1980-01-01"),
    Address("1 Station Road", "Town Centre", Some("Sometown"), Some("Anyshire"), Some("UK"), "AB12 3CD"))

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
              "line5": "UK",
              "postcode": "AB12 3CD"
            }
     }""".stripMargin

}
