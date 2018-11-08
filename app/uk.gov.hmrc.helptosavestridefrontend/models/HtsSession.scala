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

import play.api.libs.json._
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}

sealed trait SessionEligibilityCheckResult

object SessionEligibilityCheckResult {

  case class Eligible(response: EligibilityCheckResponse) extends SessionEligibilityCheckResult

  case class Ineligible(response: EligibilityCheckResponse, manualCreationAllowed: Boolean) extends SessionEligibilityCheckResult

  case object AlreadyHasAccount extends SessionEligibilityCheckResult

  def fromEligibilityCheckResult(result: EligibilityCheckResult): SessionEligibilityCheckResult = result match {
    case EligibilityCheckResult.Eligible(value)      ⇒ Eligible(value)
    case EligibilityCheckResult.Ineligible(value)    ⇒ Ineligible(value, manualCreationAllowed = false)
    case EligibilityCheckResult.AlreadyHasAccount(_) ⇒ AlreadyHasAccount
  }

  implicit val format: Format[SessionEligibilityCheckResult] = new Format[SessionEligibilityCheckResult] {
    override def writes(u: SessionEligibilityCheckResult): JsValue = {
      val (code, result, manualCreationAllowed) = u match {
        case Eligible(value)                   ⇒ (1, Some(value), None)
        case Ineligible(value, manualCreation) ⇒ (2, Some(value), Some(manualCreation))
        case AlreadyHasAccount                 ⇒ (3, None, None)
      }

      val fields: List[(String, JsValue)] =
        List("code" → Some(JsNumber(code)),
          "result" → result.map(Json.toJson(_)),
          "manualCreationAllowed" → manualCreationAllowed.map(Json.toJson(_))
        ).collect {
            case (key, Some(value)) ⇒ key → value
          }

      JsObject(fields)
    }

    override def reads(json: JsValue): JsResult[SessionEligibilityCheckResult] = {
      ((json \ "code").validate[Int],
        (json \ "result").validateOpt[EligibilityCheckResponse],
        (json \ "manualCreationAllowed").validate[Boolean]) match {
          case (JsSuccess(1, _), JsSuccess(Some(value), _), _) ⇒ JsSuccess(Eligible(value))
          case (JsSuccess(2, _), JsSuccess(Some(value), _), JsSuccess(manualCreationAllowed, _)) ⇒ JsSuccess(Ineligible(value, manualCreationAllowed))
          case (JsSuccess(3, _), JsSuccess(None, _), _) ⇒ JsSuccess(AlreadyHasAccount)
          case _ ⇒ JsError(s"error during parsing eligibility from json $json")
        }
    }
  }
}

case class HtsSession(userInfo: SessionEligibilityCheckResult, nSIUserInfo: NSIPayload, detailsConfirmed: Boolean = false)

object HtsSession {
  implicit val format: Format[HtsSession] = Json.format[HtsSession]
}
