/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetails
import uk.gov.hmrc.helptosavestridefrontend.models.NSIPayload.ContactDetails
import uk.gov.hmrc.helptosavestridefrontend.util.NINO

case class NSIPayload(forename:            String,
                      surname:             String,
                      dateOfBirth:         LocalDate,
                      nino:                String,
                      contactDetails:      ContactDetails,
                      registrationChannel: String,
                      systemId:            String)

object NSIPayload {

  case class ContactDetails(address1:                String,
                            address2:                String,
                            address3:                Option[String],
                            address4:                Option[String],
                            address5:                Option[String],
                            postcode:                String,
                            countryCode:             Option[String],
                            phoneNumber:             Option[String] = None,
                            communicationPreference: String         = "00")

  implicit val dateFormat: Format[LocalDate] = {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
    Format[LocalDate](localDateReads(formatter), temporalWrites[LocalDate, DateTimeFormatter](formatter))
  }

  implicit val contactDetailsFormat: Format[ContactDetails] = Json.format[ContactDetails]

  implicit val nsiUserInfoFormat: Format[NSIPayload] = Json.format[NSIPayload]

  def apply(forename:       String,
            surname:        String,
            dateOfBirth:    LocalDate,
            nino:           String,
            contactDetails: ContactDetails): NSIPayload = NSIPayload(forename, surname, dateOfBirth, nino, contactDetails, "callCentre", "STRIDE")

  def apply(details: ApplicantDetails, nino: NINO): NSIPayload =
    NSIPayload(details.forename, details.surname, details.dateOfBirth, nino,
               ContactDetails(details.address1, details.address2, details.address3,
                              details.address4, details.address5, details.postcode, Some(details.countryCode)))

}
