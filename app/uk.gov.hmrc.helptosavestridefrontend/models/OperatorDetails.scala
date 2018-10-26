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

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.helptosavestridefrontend.util.NINO

case class PersonalInformationDisplayed(nino: NINO, name: String, dateOfBirth: Option[LocalDate], address: List[String])

object PersonalInformationDisplayed {
  implicit val format: Format[PersonalInformationDisplayed] = Json.format[PersonalInformationDisplayed]
}

case class OperatorDetails(roles: List[String], pid: Option[String], name: String, email: String)

object OperatorDetails {
  implicit val format: Format[OperatorDetails] = Json.format[OperatorDetails]
}
