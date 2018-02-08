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

import play.api.libs.json.{JsSuccess, Json}
import uk.gov.hmrc.helptosavestridefrontend.TestSupport
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult.Eligible

class EligibilityCheckResultSpec extends TestSupport {

  "EligibilityCheckResult" when {

    "the result is Eligible" must {

      val jsonString = """{"code":1,"result":{"result":"eligible","resultCode":1,"reason":"tax credits","reasonCode":2}}"""
      val result = Eligible(EligibilityCheckResponse("eligible", 1, "tax credits", 2))

      "write to json as expected" in {
        Json.toJson(result) === jsonString
      }

      "read the json as expected" in {
        Json.parse(jsonString).validate[EligibilityCheckResult] shouldBe JsSuccess(result)
      }
    }

    "the result is InEligible" must {

      val jsonString = """{"code":2,"result":{"result":"in-eligible","resultCode":2,"reason":"no tax credits","reasonCode":2}}"""
      val result = Eligible(EligibilityCheckResponse("in-eligible", 2, "no tax credits", 2))

      "write to json as expected" in {
        Json.toJson(result) === jsonString
      }

      "read the json as expected" in {
        Json.parse(jsonString).validate[EligibilityCheckResult] shouldBe JsSuccess(result)

      }
    }

    "the result is AlreadyHasAccount" must {

      val jsonString = """{"code":3,"result":{"result":"AlreadyHasAccount","resultCode":3,"reason":"AlreadyHasAccount","reasonCode":7}}"""
      val result = Eligible(EligibilityCheckResponse("AlreadyHasAccount", 3, "AlreadyHasAccount", 7))

      "write to json as expected" in {
        Json.toJson(result) === jsonString
      }

      "read the json as expected" in {
        Json.parse(jsonString).validate[EligibilityCheckResult] shouldBe JsSuccess(result)

      }
    }
  }

}
