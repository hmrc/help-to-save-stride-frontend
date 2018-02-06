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

import cats.data.EitherT
import cats.instances.future._
import play.api.i18n.MessagesApi
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosavestridefrontend.forms.NINOValidation
import uk.gov.hmrc.helptosavestridefrontend.models.PayePersonalDetails
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult.Eligible
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavestridefrontend.util.NINO
import uk.gov.hmrc.helptosavestridefrontend.{AuthSupport, CSRFSupport, TestData, TestSupport}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class StrideControllerSpec extends TestSupport with AuthSupport with CSRFSupport with TestData {

  val helpToSaveConnector = mock[HelpToSaveConnector]

  private implicit val ninoValidation: NINOValidation = new NINOValidation

  def mockEligibility(nino: NINO)(result: Either[String, EligibilityCheckResult]) =
    (helpToSaveConnector.getEligibility(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockPayeDetails(nino: NINO)(result: Either[String, PayePersonalDetails]) =
    (helpToSaveConnector.getPayePersonalDetails(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  lazy val controller =
    new StrideController(mockAuthConnector,
                         helpToSaveConnector,
                         fakeApplication.injector.instanceOf[FrontendAppConfig],
                         fakeApplication.injector.instanceOf[MessagesApi])

  "The StrideController" when {

    "getting the getEligibilityPage" must {

      "show the page when the user is authorised" in {
        mockSuccessfulAuthorisation()

        val result = controller.getEligibilityPage(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("Help to Save - Enter National Insurance number")
      }
    }

    "getting the you-are-eligible page" must {

      "show the page when the user is authorised" in {
        mockSuccessfulAuthorisation()

        val result = controller.youAreEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("you are eligible")
      }
    }

    "getting the you-are-not-eligible page" must {

      "show the page when the user is authorised" in {
        mockSuccessfulAuthorisation()

        val result = controller.youAreNotEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("you are NOT eligible")
      }
    }

    "getting the account-already-exists page" must {

      "show the page when the user is authorised" in {
        mockSuccessfulAuthorisation()

        val result = controller.accountAlreadyExists(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("Account already exists")
      }
    }

    "checking the eligibility and retrieving paye details" must {

      val ninoEndoded = "QUUxMjM0NTZD"

      val emptyECResponse = EligibilityCheckResponse("No tax credit record found for user's NINO", 2, "", -1)
      val eligibleECResponse = EligibilityCheckResponse("eligible", 1, "tax credits", 7)

      "handle the forms with invalid input" in {
        mockSuccessfulAuthorisation()
        val fakePostRequest = fakeRequestWithCSRFToken.withFormUrlEncodedBody("nino" → "blah")
        val result = controller.checkEligibilityAndGetPersonalInfo(fakePostRequest)
        status(result) shouldBe OK
        contentAsString(result) should include("invalid input, sample valid input is : AE123456C")
      }

      "handle the case where user is not eligible" in {
        mockSuccessfulAuthorisation()
        mockEligibility(ninoEndoded)(Right(EligibilityCheckResult.Ineligible(emptyECResponse)))

        val fakePostRequest = fakeRequestWithCSRFToken.withFormUrlEncodedBody("nino" → nino)
        val result = controller.checkEligibilityAndGetPersonalInfo(fakePostRequest)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/help-to-save/you-are-not-eligible")
      }

      "handle the case where user has already got account" in {
        mockSuccessfulAuthorisation()
        mockEligibility(ninoEndoded)(Right(EligibilityCheckResult.AlreadyHasAccount(eligibleECResponse)))

        val fakePostRequest = fakeRequestWithCSRFToken.withFormUrlEncodedBody("nino" → nino)
        val result = controller.checkEligibilityAndGetPersonalInfo(fakePostRequest)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/help-to-save/account-already-exists")
      }

      "handle the case where user is eligible and paye-details exist" in {
        mockSuccessfulAuthorisation()
        mockEligibility(ninoEndoded)(Right(Eligible(eligibleECResponse)))
        mockPayeDetails(ninoEndoded)(Right(ppDetails))

        val fakePostRequest = fakeRequestWithCSRFToken.withFormUrlEncodedBody("nino" → nino)
        val result = controller.checkEligibilityAndGetPersonalInfo(fakePostRequest)
        status(result) shouldBe OK
        contentAsString(result) should include("Help to Save - You are eligible")
        contentAsString(result) should include("you are eligible")
      }

      "handle the errors during eligibility check" in {
        mockSuccessfulAuthorisation()
        mockEligibility(ninoEndoded)(Left("unexpected error"))

        val fakePostRequest = fakeRequestWithCSRFToken.withFormUrlEncodedBody("nino" → nino)
        val result = controller.checkEligibilityAndGetPersonalInfo(fakePostRequest)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "handle the errors during retrieve paye-personal-details" in {
        mockSuccessfulAuthorisation()
        mockEligibility(ninoEndoded)(Right(Eligible(eligibleECResponse)))
        mockPayeDetails(ninoEndoded)(Left("unexpected error"))

        val fakePostRequest = fakeRequestWithCSRFToken.withFormUrlEncodedBody("nino" → nino)
        val result = controller.checkEligibilityAndGetPersonalInfo(fakePostRequest)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

    }

  }

}
