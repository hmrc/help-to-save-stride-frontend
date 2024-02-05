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

package uk.gov.hmrc.helptosavestridefrontend.controllers

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.helptosavestridefrontend.models.SessionEligibilityCheckResult
import uk.gov.hmrc.helptosavestridefrontend.models.SessionEligibilityCheckResult._
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}

class SessionEligibilityCheckResultSpec extends AnyWordSpec with Matchers {

  "SessionEligibilityCheckResult" must {

    val eligibilityCheckResult = EligibilityCheckResponse("result", 1, "reason", 2)

    "have a Format instance" in {
      List[SessionEligibilityCheckResult](
        Eligible(eligibilityCheckResult),
        Ineligible(eligibilityCheckResult, manualCreationAllowed = true),
        AlreadyHasAccount
      ).foreach { result =>
        withClue(s"For $result: ") {
          Json.fromJson[SessionEligibilityCheckResult](Json.toJson(result)).asOpt shouldBe Some(result)
        }
      }

    }

    "have a method which converts from EligibilityCheckResult" in {
      fromEligibilityCheckResult(EligibilityCheckResult.Eligible(eligibilityCheckResult)) shouldBe Eligible(
        eligibilityCheckResult)
      fromEligibilityCheckResult(EligibilityCheckResult.Ineligible(eligibilityCheckResult)) shouldBe Ineligible(
        eligibilityCheckResult,
        false)
      fromEligibilityCheckResult(EligibilityCheckResult.AlreadyHasAccount(eligibilityCheckResult)) shouldBe AlreadyHasAccount

    }

  }

}
