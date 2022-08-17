/*
 * Copyright 2021 HM Revenue & Customs
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

import cats.instances.string._
import cats.syntax.eq._
import play.api.data.Form
import play.api.data.Forms._
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetailsValidation.ErrorMessages
import uk.gov.hmrc.helptosavestridefrontend.models.NSIPayload
import uk.gov.hmrc.helptosavestridefrontend.views.ApplicantDetailsForm.Ids

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
      Ids.address3 → nsiPayload.contactDetails.address3,
      Ids.address4 → nsiPayload.contactDetails.address4,
      Ids.address5 → nsiPayload.contactDetails.address5,
      Ids.countryCode → nsiPayload.contactDetails.countryCode
    )

    applicantDetailsForm.copy(data =
      Map(
        Ids.forename → nsiPayload.forename,
        Ids.surname → nsiPayload.surname,
        Ids.dobDay → nsiPayload.dateOfBirth.getDayOfMonth.toString,
        Ids.dobMonth → nsiPayload.dateOfBirth.getMonthValue.toString,
        Ids.dobYear → nsiPayload.dateOfBirth.getYear.toString,
        Ids.address1 → nsiPayload.contactDetails.address1,
        Ids.address2 → nsiPayload.contactDetails.address2,
        Ids.postcode → nsiPayload.contactDetails.postcode
      ) ++
        maybeFields.collect{ case (key, Some(value)) ⇒ key → value }
    )
  }

  def applicantDetailsForm(implicit applicantDetailsValidation: ApplicantDetailsValidation, clock: Clock): Form[ApplicantDetails] = Form(
    mapping(
      Ids.forename → of(applicantDetailsValidation.forenameFormatter),
      Ids.surname → of(applicantDetailsValidation.surnameFormatter),
      Ids.dobDay → of(applicantDetailsValidation.dayOfMonthFormatter),
      Ids.dobMonth → of(applicantDetailsValidation.monthFormatter),
      Ids.dobYear → of(applicantDetailsValidation.yearFormatter),
      Ids.dateOfBirth → of(applicantDetailsValidation.dateOfBirthFormatter),
      Ids.address1 → of(applicantDetailsValidation.addressLine1Formatter),
      Ids.address2 → of(applicantDetailsValidation.addressLine2Formatter),
      Ids.address3 → of(applicantDetailsValidation.addressLine3Formatter),
      Ids.address4 → of(applicantDetailsValidation.addressLine4Formatter),
      Ids.address5 → of(applicantDetailsValidation.addressLine5Formatter),
      Ids.postcode → of(applicantDetailsValidation.postcodeFormatter),
      Ids.countryCode → text
    ){
        case (forename, surname, _, _, _, dob, address1, address2, address3, address4, address5, postcode, countryCode) ⇒
          ApplicantDetails(forename, surname, dob, address1, address2, address3, address4, address5, postcode, countryCode)
      }{ details ⇒
        Some((details.forename, details.surname, details.dateOfBirth.getDayOfMonth, details.dateOfBirth.getMonthValue,
          details.dateOfBirth.getYear, details.dateOfBirth, details.address1, details.address2, details.address3, details.address4, details.address5,
          details.postcode, details.countryCode))
      } verifying (ErrorMessages.dateOfBirthInFuture, _.dateOfBirth.isBefore(LocalDate.now(clock)))
  )

  implicit class ApplicantDetailsFormOps(val a: Form[ApplicantDetails]) extends AnyVal {

    private def hasErrorMessage(id: String, errorMessage: String): Boolean =
      a.error(id).exists(_.message === errorMessage)

    def hasForenameTooLong: Boolean = hasErrorMessage(Ids.forename, ErrorMessages.forenameTooLong)

    def hasForenameEmpty: Boolean = hasErrorMessage(Ids.forename, ErrorMessages.forenameEmpty)

    def hasSurnameTooLong: Boolean = hasErrorMessage(Ids.surname, ErrorMessages.surnameTooLong)

    def hasSurnameEmpty: Boolean = hasErrorMessage(Ids.surname, ErrorMessages.surnameEmpty)

    def hasDayOfMonthEmpty: Boolean = hasErrorMessage(Ids.dobDay, ErrorMessages.dayOfMonthEmpty)

    def hasDayOfMonthInvalid: Boolean = hasErrorMessage(Ids.dobDay, ErrorMessages.dayOfMonthInvalid)

    def hasMonthEmpty: Boolean = hasErrorMessage(Ids.dobMonth, ErrorMessages.monthEmpty)

    def hasMonthInvalid: Boolean = hasErrorMessage(Ids.dobMonth, ErrorMessages.monthInvalid)

    def hasYearEmpty: Boolean = hasErrorMessage(Ids.dobYear, ErrorMessages.yearEmpty)

    def hasYearInvalid: Boolean = hasErrorMessage(Ids.dobYear, ErrorMessages.yearInvalid)

    def hasYearTooEarly: Boolean = hasErrorMessage(Ids.dobYear, ErrorMessages.yearTooEarly)

    def hasDateOfBirthInvalid: Boolean = a.errors.exists(_.message === ErrorMessages.dateOfBirthInvalid)

    def hasDateOfBirthInFuture: Boolean = a.errors.exists(_.message === ErrorMessages.dateOfBirthInFuture)

    def hasAddress1TooLong: Boolean = hasErrorMessage(Ids.address1, ErrorMessages.address1TooLong)

    def hasAddress1Empty: Boolean = hasErrorMessage(Ids.address1, ErrorMessages.address1Empty)

    def hasAddress2TooLong: Boolean = hasErrorMessage(Ids.address2, ErrorMessages.address2TooLong)

    def hasAddress2Empty: Boolean = hasErrorMessage(Ids.address2, ErrorMessages.address2Empty)

    def hasAddress3TooLong: Boolean = hasErrorMessage(Ids.address3, ErrorMessages.address3TooLong)

    def hasAddress4TooLong: Boolean = hasErrorMessage(Ids.address4, ErrorMessages.address4TooLong)

    def hasAddress5TooLong: Boolean = hasErrorMessage(Ids.address5, ErrorMessages.address5TooLong)

    def hasPostcodeTooLong: Boolean = hasErrorMessage(Ids.postcode, ErrorMessages.postcodeTooLong)

    def hasPostcodeEmpty: Boolean = hasErrorMessage(Ids.postcode, ErrorMessages.postCodeEmpty)

  }

}

