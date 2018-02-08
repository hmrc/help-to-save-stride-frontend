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

package uk.gov.hmrc.helptosavestridefrontend.models.eligibility

import play.api.libs.json._

sealed trait EligibilityCheckResult {
  val value: EligibilityCheckResponse
}

object EligibilityCheckResult {

  case class Eligible(value: EligibilityCheckResponse) extends EligibilityCheckResult

  case class Ineligible(value: EligibilityCheckResponse) extends EligibilityCheckResult

  case class AlreadyHasAccount(value: EligibilityCheckResponse) extends EligibilityCheckResult

  implicit val format: Format[EligibilityCheckResult] = new Format[EligibilityCheckResult] {
    override def writes(result: EligibilityCheckResult): JsValue = {
      val (a, b) = result match {
        case Eligible(value)          ⇒ (1, value)
        case Ineligible(value)        ⇒ (2, value)
        case AlreadyHasAccount(value) ⇒ (3, value)
      }

      JsObject(List("code" -> JsNumber(a), "result" -> Json.toJson(b)))
    }

    override def reads(json: JsValue): JsResult[EligibilityCheckResult] = {
      ((json \ "code").validate[Int], (json \ "result").validate[EligibilityCheckResponse]) match {
        case (JsSuccess(1, _), JsSuccess(value, _)) ⇒ JsSuccess(Eligible(value))
        case (JsSuccess(2, _), JsSuccess(value, _)) ⇒ JsSuccess(Eligible(value))
        case (JsSuccess(3, _), JsSuccess(value, _)) ⇒ JsSuccess(Eligible(value))
        case _                                      ⇒ JsError(s"error during parsing eligibility from json $json")
      }
    }
  }

}

