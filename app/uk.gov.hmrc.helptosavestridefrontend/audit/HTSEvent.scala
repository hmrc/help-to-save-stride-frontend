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

package uk.gov.hmrc.helptosavestridefrontend.audit

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.models.{OperatorDetails, PersonalInformationDisplayed}
import uk.gov.hmrc.helptosavestridefrontend.util.NINO
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

trait HTSEvent {
  val value: ExtendedDataEvent
}

object HTSEvent {
  def apply(appName: String, auditType: String, detail: JsValue, transactionName: String, path: String)(
    implicit hc: HeaderCarrier): ExtendedDataEvent =
    ExtendedDataEvent(appName, auditType, detail = detail, tags = hc.toAuditTags(transactionName, path))

}

case class PersonalInformationDisplayedToOperator(
  piDisplayed: PersonalInformationDisplayed,
  operatorDetails: OperatorDetails,
  path: String)(implicit hc: HeaderCarrier, appConfig: FrontendAppConfig)
    extends HTSEvent {
  val value: ExtendedDataEvent = HTSEvent(
    appConfig.appName,
    "PersonalInformationDisplayedToOperator",
    Json.obj(
      "detailsDisplayed" -> Json.toJson(piDisplayed),
      "operatorDetails"  -> Json.toJson(operatorDetails)
    ),
    "personal-information-displayed-to-stride-operator",
    path
  )
}

case class ManualAccountCreationSelected(nino: NINO, path: String, operatorDetails: OperatorDetails)(
  implicit hc: HeaderCarrier,
  appConfig: FrontendAppConfig)
    extends HTSEvent {
  val value: ExtendedDataEvent = HTSEvent(
    appConfig.appName,
    "ManualAccountCreationSelected",
    Json.obj(
      "nino"            -> nino,
      "operatorDetails" -> Json.toJson(operatorDetails)
    ),
    "manual-account-created",
    path
  )
}
