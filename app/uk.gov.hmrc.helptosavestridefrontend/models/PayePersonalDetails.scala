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

package uk.gov.hmrc.helptosavestridefrontend.models

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json.Reads.localDateReads
import play.api.libs.json.Writes.temporalWrites
import play.api.libs.json._

case class PayePersonalDetails(name:        Name,
                               dateOfBirth: LocalDate,
                               address:     Address,
                               phoneNumber: Option[TelePhoneNumber])

case class Name(title:                   Option[String],
                firstForenameOrInitial:  String,
                secondForenameOrInitial: Option[String],
                surname:                 String
)

case class Address(line1:       String,
                   line2:       String,
                   line3:       Option[String],
                   line4:       Option[String],
                   line5:       Option[String],
                   postcode:    String,
                   countryCode: Option[String]
)

case class TelePhoneNumber(telephoneNumber: String, telephoneType: Int)

object PayePersonalDetails {

  implicit val nameFormat: Format[Name] = Json.format[Name]

  implicit val addressFormat: Format[Address] = Json.format[Address]

  implicit val phoneNumberFormat: Format[TelePhoneNumber] = Json.format[TelePhoneNumber]

  implicit val dateFormat: Format[LocalDate] = {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    Format[LocalDate](localDateReads(formatter), temporalWrites[LocalDate, DateTimeFormatter](formatter))
  }

  implicit val format: Format[PayePersonalDetails] = Json.format[PayePersonalDetails]
}
