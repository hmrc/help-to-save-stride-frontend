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

package uk.gov.hmrc.helptosavestridefrontend.views

import play.api.data.Form
import play.api.i18n.Messages
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetails
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetailsForm.ApplicantDetailsFormOps

import scala.annotation.tailrec
object ApplicantDetailsForm {

  object Ids {
    val forename: String = "forename"
    val surname: String = "surname"
    val dateOfBirth: String = "dob"
    val dobDay: String = "dob-day"
    val dobMonth: String = "dob-month"
    val dobYear: String = "dob-year"
    val address1: String = "address1"
    val address2: String = "address2"
    val address3: String = "address3"
    val address4: String = "address4"
    val address5: String = "address5"
    val postcode: String = "postcode"
    val countryCode: String = "countryCode"
  }

  case class ErrorMessages(forename:     Option[String],
                           surname:      Option[String],
                           dateOfBirth:  Option[String],
                           addressLine1: Option[String],
                           addressLine2: Option[String],
                           addressLine3: Option[String],
                           addressLine4: Option[String],
                           addressLine5: Option[String],
                           postcode:     Option[String]) {

    val errors: List[(String, String)] =
      List(
        Ids.forename → forename,
        Ids.surname → surname,
        Ids.dateOfBirth → dateOfBirth,
        Ids.address1 → addressLine1,
        Ids.address2 → addressLine2,
        Ids.address3 → addressLine3,
        Ids.address4 → addressLine4,
        Ids.address5 → addressLine5,
        Ids.postcode → postcode).collect { case (k, Some(e)) => k → e }

    val errorExists: Boolean = errors.nonEmpty
  }

  def errorMessages(form: Form[ApplicantDetails])(implicit appConfig: FrontendAppConfig, messages: Messages): ErrorMessages = { // scalastyle:ignore
    import appConfig.FormValidation._

    val forenameErrorMessage =
      errorMessageKey(
        form.hasForenameTooLong → messages("hts.customer-eligible.enter-details.error.forename.too-long", forenameMaxTotalLength),
        form.hasForenameEmpty → messages("hts.customer-eligible.enter-details.error.forename.empty")
      )

    val surnameErrorMessage =
      errorMessageKey(
        form.hasSurnameTooLong → messages("hts.customer-eligible.enter-details.error.surname.too-long", surnameMaxTotalLength),
        form.hasSurnameEmpty → messages("hts.customer-eligible.enter-details.error.surname.empty")
      )

    val address1ErrorMessage =
      errorMessageKey(
        form.hasAddress1TooLong → messages("hts.customer-eligible.enter-details.error.address-1.too-long", addressLineMaxTotalLength),
        form.hasAddress1Empty → messages("hts.customer-eligible.enter-details.error.address-1.empty")
      )

    val address2ErrorMessage =
      errorMessageKey(
        form.hasAddress2TooLong → messages("hts.customer-eligible.enter-details.error.address-2.too-long", addressLineMaxTotalLength),
        form.hasAddress2Empty → messages("hts.customer-eligible.enter-details.error.address-2.empty")
      )

    val address3ErrorMessage =
      errorMessageKey(form.hasAddress3TooLong → messages("hts.customer-eligible.enter-details.error.address-3.too-long", addressLineMaxTotalLength))

    val address4ErrorMessage =
      errorMessageKey(form.hasAddress4TooLong → messages("hts.customer-eligible.enter-details.error.address-4.too-long", addressLineMaxTotalLength))

    val address5ErrorMessage =
      errorMessageKey(form.hasAddress5TooLong → messages("hts.customer-eligible.enter-details.error.address-5.too-long", addressLineMaxTotalLength))

    val postcodeErrorMessage =
      errorMessageKey(
        form.hasPostcodeTooLong → messages("hts.customer-eligible.enter-details.error.postcode.too-long", postcodeMaxTotalLength),
        form.hasPostcodeEmpty → messages("hts.customer-eligible.enter-details.error.postcode.empty")
      )

    val dateOfBirthErrorMessage = {
      val hasInvalidField =
        form.hasDayOfMonthInvalid || form.hasMonthInvalid || form.hasYearInvalid || form.hasYearTooEarly || form.hasDateOfBirthInFuture

      val nullFieldErrorMessage: Option[String] = (hasInvalidField, form.hasDayOfMonthEmpty, form.hasMonthEmpty, form.hasYearEmpty) match {
        case (true, false, false, false) => None // error message will be defined below
        case (true, _, _, _)             => Some(messages("hts.customer-eligible.enter-details.error.date-of-birth.enter-date-of-birth-and-include"))
        case (_, true, true, true)       => Some(messages("hts.customer-eligible.enter-details.error.date-of-birth.enter-a-date-of-birth"))
        case (_, true, false, false)     => Some(messages("hts.customer-eligible.enter-details.error.date-of-birth.must-include-day"))
        case (_, false, true, false)     => Some(messages("hts.customer-eligible.enter-details.error.date-of-birth.must-include-month"))
        case (_, false, false, true)     => Some(messages("hts.customer-eligible.enter-details.error.date-of-birth.must-include-year"))
        case (_, true, true, false)      => Some(messages("hts.customer-eligible.enter-details.error.date-of-birth.must-include-day-month"))
        case (_, true, false, true)      => Some(messages("hts.customer-eligible.enter-details.error.date-of-birth.must-include-day-year"))
        case (_, false, true, true)      => Some(messages("hts.customer-eligible.enter-details.error.date-of-birth.must-include-month-year"))
        case (_, false, false, false)    => None
      }

      val invalidFieldErrorMessage: Option[String] =
        (form.hasDayOfMonthInvalid, form.hasMonthInvalid, form.hasYearInvalid, form.hasYearTooEarly || form.hasDateOfBirthInFuture) match {
          case (false, false, false, _)    => None
          case (true, false, false, false) => Some(messages("hts.customer-eligible.enter-details.error.date-of-birth.enter-real-day"))
          case (false, true, false, false) => Some(messages("hts.customer-eligible.enter-details.error.date-of-birth.enter-real-month"))
          case (false, false, true, false) => Some(messages("hts.customer-eligible.enter-details.error.date-of-birth.enter-real-year"))
          case (_, _, _, _)                => Some(messages("hts.customer-eligible.enter-details.error.date-of-birth.enter-real-date-of-birth"))
        }

      val dateInvalidErrorMessage =
        errorMessageKey(
          form.hasDateOfBirthInFuture → messages("hts.customer-eligible.enter-details.error.date-of-birth.must-be-in-past"),
          form.hasYearTooEarly → messages("hts.customer-eligible.enter-details.error.date-of-birth.day.too-early"),
          form.hasDateOfBirthInvalid → messages("hts.customer-eligible.enter-details.error.date-of-birth.enter-real-date-of-birth")
        )

      nullFieldErrorMessage.orElse(invalidFieldErrorMessage).orElse(dateInvalidErrorMessage)
    }

    ErrorMessages(forenameErrorMessage, surnameErrorMessage, dateOfBirthErrorMessage,
                  address1ErrorMessage, address2ErrorMessage, address3ErrorMessage, address4ErrorMessage, address5ErrorMessage, postcodeErrorMessage)
  }

  private def errorMessageKey(x: (Boolean, String)*): Option[String] = {
      @tailrec
      def loop(l: List[(Boolean, String)]): Option[String] = l match {
        case Nil                               => None
        case (predicate, errorMessage) :: tail => if (predicate) { Some(errorMessage) } else { loop(tail) }
      }
    loop(x.toList)
  }

}
