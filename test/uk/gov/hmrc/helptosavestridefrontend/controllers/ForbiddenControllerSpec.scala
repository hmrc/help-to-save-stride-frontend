/*
 * Copyright 2024 HM Revenue & Customs
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

import play.api.http.Status
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavestridefrontend.TestSupport

class ForbiddenControllerSpec extends TestSupport {

  "The ForbiddenController" must {

    val controller = new ForbiddenController(testMcc, errorHandler)

    "return a forbidden status" in {
      val result = controller.forbidden(FakeRequest())
      status(result) shouldBe Status.FORBIDDEN
      contentAsString(result) shouldBe "Please ask the HtS Dev team for permissions to access this site"
    }

  }

}
