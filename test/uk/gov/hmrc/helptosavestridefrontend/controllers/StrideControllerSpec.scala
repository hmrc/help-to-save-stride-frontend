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

package uk.gov.hmrc.helptosavestridefrontend.controllers

import play.api.i18n.MessagesApi
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavestridefrontend.{AuthSupport, TestSupport}

class StrideControllerSpec extends TestSupport with AuthSupport {

  lazy val controller =
    new StrideController(mockAuthConnector, fakeApplication.injector.instanceOf[MessagesApi])

  "The StrideController" when {

    "getting the start page" must {

      "show the start page when the user is authorised" in {
        mockSuccessfulAuthorisation()

        val result = controller.getStartPage(FakeRequest())
        status(result) shouldBe OK
        contentAsString(result) should include("This is a temporary start page")

      }
    }

  }

}
