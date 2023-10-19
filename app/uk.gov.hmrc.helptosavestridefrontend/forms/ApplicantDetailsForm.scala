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

package uk.gov.hmrc.helptosavestridefrontend.forms

import java.time.{Clock, LocalDate}
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetailsValidation.ErrorMessages
import uk.gov.hmrc.helptosavestridefrontend.forms.DateFormFormatter._
import uk.gov.hmrc.helptosavestridefrontend.models.NSIPayload
import uk.gov.hmrc.helptosavestridefrontend.views.ApplicantDetailsForm.Ids
import play.api.data.Form
import play.api.data.Forms._
import scala.collection.immutable.Seq

case class ApplicantDetails(forename:    String,
                            surname:     String,
                            dateOfBirth: LocalDate,
                            address1:    String,
                            address2:    String,
                            address3:    Option[String],
                            address4:    Option[String],
                            address5:    Option[String],
                            postcode:    String,
                            countryCode: String)

object ApplicantDetailsForm {

  def apply(nsiPayload: NSIPayload)(implicit applicantDetailsValidation: ApplicantDetailsValidation, clock: Clock): Form[ApplicantDetails] = {
    val maybeFields = Map(
      Ids.address3 -> nsiPayload.contactDetails.address3,
      Ids.address4 -> nsiPayload.contactDetails.address4,
      Ids.address5 -> nsiPayload.contactDetails.address5,
      Ids.countryCode -> nsiPayload.contactDetails.countryCode
    )

    applicantDetailsForm.copy(data =
      Map(
        Ids.forename -> nsiPayload.forename,
        Ids.surname -> nsiPayload.surname,
        Ids.dobDay -> nsiPayload.dateOfBirth.getDayOfMonth.toString,
        Ids.dobMonth -> nsiPayload.dateOfBirth.getMonthValue.toString,
        Ids.dobYear -> nsiPayload.dateOfBirth.getYear.toString,
        Ids.address1 -> nsiPayload.contactDetails.address1,
        Ids.address2 -> nsiPayload.contactDetails.address2,
        Ids.postcode -> nsiPayload.contactDetails.postcode
      ) ++
        maybeFields.collect{ case (key, Some(value)) => key -> value }
    )
  }

  def applicantDetailsForm(implicit applicantDetailsValidation: ApplicantDetailsValidation, clock: Clock): Form[ApplicantDetails] = Form(
    mapping(
      Ids.forename -> of(applicantDetailsValidation.nameFormatter),
      Ids.surname -> of(applicantDetailsValidation.nameFormatter),
      Ids.dateOfBirth -> of(DateFormFormatter.dateFormFormatter(
        maximumDateInclusive = Some(LocalDate.now(clock)),
        minimumDateInclusive = Some(LocalDate.of(1900, 1, 1)),
        "dob-day",
        "dob-month",
        "dob-year",
        "dob",
        tooRecentArgs        = Seq("today"),
        tooFarInPastArgs     = Seq.empty
      )),
      Ids.address1 -> of(applicantDetailsValidation.addressLineFormatter),
      Ids.address2 -> of(applicantDetailsValidation.addressLineFormatter),
      Ids.address3 -> of(applicantDetailsValidation.addressOptionalLineFormatter),
      Ids.address4 -> of(applicantDetailsValidation.addressOptionalLineFormatter),
      Ids.address5 -> of(applicantDetailsValidation.addressOptionalLineFormatter),
      Ids.postcode -> of(applicantDetailsValidation.postcodeFormatter),
      Ids.countryCode -> text
    )(ApplicantDetails.apply)(ApplicantDetails.unapply)
  )
}
