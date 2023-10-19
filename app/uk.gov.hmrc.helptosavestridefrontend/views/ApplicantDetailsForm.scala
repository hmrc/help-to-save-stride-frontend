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
                           dobDay:       Option[String],
                           dobMonth:     Option[String],
                           dobYear:      Option[String],
                           addressLine1: Option[String],
                           addressLine2: Option[String],
                           addressLine3: Option[String],
                           addressLine4: Option[String],
                           addressLine5: Option[String],
                           postcode:     Option[String]) {

    val errors: List[(String, String)] =
      List(
        Ids.forename -> forename,
        Ids.surname -> surname,
        Ids.dateOfBirth -> dateOfBirth,
        Ids.address1 -> addressLine1,
        Ids.address2 -> addressLine2,
        Ids.address3 -> addressLine3,
        Ids.address4 -> addressLine4,
        Ids.address5 -> addressLine5,
        Ids.postcode -> postcode).collect { case (k, Some(e)) => k -> e }

    val errorExists: Boolean = errors.nonEmpty
  }

}
