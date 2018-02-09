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
import play.api.libs.json.{Reads, Writes}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.connectors.{HelpToSaveConnector, KeyStoreConnector}
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.UserSessionInfo
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.UserSessionInfo._
import uk.gov.hmrc.helptosavestridefrontend.models.PayePersonalDetails
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavestridefrontend.util.NINO
import uk.gov.hmrc.helptosavestridefrontend.{AuthSupport, CSRFSupport, TestData, TestSupport}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class StrideControllerSpec extends TestSupport with AuthSupport with CSRFSupport with TestData {

  val helpToSaveConnector = mock[HelpToSaveConnector]

  val keystoreConnector = mock[KeyStoreConnector]

  def mockEligibility(nino: NINO)(result: Either[String, EligibilityCheckResult]) =
    (helpToSaveConnector.getEligibility(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockPayeDetails(nino: NINO)(result: Either[String, PayePersonalDetails]) =
    (helpToSaveConnector.getPayePersonalDetails(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockKeyStoreGet(key: String)(result: Either[String, Option[UserSessionInfo]]) =
    (keystoreConnector.get(_: String)(_: Reads[UserSessionInfo], _: HeaderCarrier, _: ExecutionContext))
      .expects(key, *, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockKeyStorePut(key: String, strideUserInfo: UserSessionInfo)(result: Either[String, Unit]) =
    (keystoreConnector.put(_: String, _: UserSessionInfo)(_: Writes[UserSessionInfo], _: HeaderCarrier, _: ExecutionContext))
      .expects(key, strideUserInfo, *, *, *)
      .returning(EitherT.fromEither[Future](result))

  lazy val controller =
    new StrideController(mockAuthConnector,
                         helpToSaveConnector,
                         keystoreConnector,
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

      test(controller.youAreEligible)
    }

    "getting the you-are-not-eligible page" must {

      test(controller.youAreNotEligible)
    }

    "getting the account-already-exists page" must {

      test(controller.accountAlreadyExists)
    }

      def test(doRequest: ⇒ Action[AnyContent]): Unit = { // scalastyle:ignore method.length

        lazy val requestWithKey = fakeRequestWithCSRFToken.withSession("stride-user-info" -> cacheKey)

        "show the /check-eligibility-page when there is no session in key-store" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockKeyStoreGet(cacheKey)(Right(None))
          }

          val result = doRequest(requestWithKey)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/help-to-save/check-eligibility-page")
        }

        "show the /check-eligibility-page when there is no key in the session" in {
          mockSuccessfulAuthorisation()

          val result = doRequest(fakeRequestWithCSRFToken)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/help-to-save/check-eligibility-page")
        }

        "show the you-are-eligible page if session is found in key-store and user is eligible" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockKeyStoreGet(cacheKey)(Right(Some(eligibleStrideUserInfo)))
          }

          val result = doRequest(requestWithKey)
          status(result) shouldBe OK
          contentAsString(result) should include("you are eligible")
        }

        "show the you-are-not-eligible page if session is found in key-store and but user is NOT eligible" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockKeyStoreGet(cacheKey)(Right(Some(inEligibleStrideUserInfo)))
          }

          val result = doRequest(requestWithKey)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/help-to-save/you-are-not-eligible")
        }

        "show the account-already-exists page if session is found in key-store and but user has an account already" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockKeyStoreGet(cacheKey)(Right(Some(accountExistsStrideUserInfo)))
          }

          val result = doRequest(requestWithKey)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/help-to-save/account-already-exists")
        }
      }

    "checking the eligibility and retrieving paye details" must {

      val ninoEndoded = "QUUxMjM0NTZD"

      val emptyECResponse = EligibilityCheckResponse("No tax credit record found for user's NINO", 2, "", -1)
      val eligibleECResponse = EligibilityCheckResponse("eligible", 1, "tax credits", 7)

        def doRequest(nino: String, cacheKey: String) = controller.checkEligibilityAndGetPersonalInfo(fakeRequest(nino, cacheKey))

      "handle the forms with invalid input" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(cacheKey)(Right(None))
        }

        val result = doRequest("in-valid-nino", cacheKey)
        status(result) shouldBe OK
        contentAsString(result) should include("invalid input, sample valid input is : AE123456C")
      }

      "handle the case where user is not eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(cacheKey)(Right(None))
          mockEligibility(ninoEndoded)(Right(EligibilityCheckResult.Ineligible(emptyECResponse)))
          mockKeyStorePut(cacheKey, Ineligible(emptyECResponse))(Right(()))
        }

        val result = doRequest(nino, cacheKey)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/help-to-save/you-are-not-eligible")
      }

      "handle the case where user has already got account" in {
        val accountExistsResponse = EligibilityCheckResponse("account exists", 3, "account exists", 7)

        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(cacheKey)(Right(None))
          mockEligibility(ninoEndoded)(Right(EligibilityCheckResult.AlreadyHasAccount(accountExistsResponse)))
          mockKeyStorePut(cacheKey, AlreadyHasAccount(accountExistsResponse))(Right(()))
        }

        val result = doRequest(nino, cacheKey)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/help-to-save/account-already-exists")
      }

      "handle the case where user is eligible and paye-details exist" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(cacheKey)(Right(None))
          mockEligibility(ninoEndoded)(Right(EligibilityCheckResult.Eligible(eligibleECResponse)))
          mockPayeDetails(ninoEndoded)(Right(ppDetails))
          mockKeyStorePut(cacheKey, EligibleWithPayePersonalDetails(eligibleECResponse, ppDetails))(Right(()))
        }

        val result = doRequest(nino, cacheKey)
        status(result) shouldBe OK
        contentAsString(result) should include("Help to Save - You are eligible")
        contentAsString(result) should include("you are eligible")
      }

      "handle the errors during eligibility check" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(cacheKey)(Right(None))
          mockEligibility(ninoEndoded)(Left("unexpected error"))
        }

        val result = doRequest(nino, cacheKey)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "handle the errors during retrieve paye-personal-details" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(cacheKey)(Right(None))
          mockEligibility(ninoEndoded)(Right(EligibilityCheckResult.Eligible(eligibleECResponse)))
          mockPayeDetails(ninoEndoded)(Left("unexpected error"))
        }

        val result = doRequest(nino, cacheKey)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "handle the errors when retrieving stride-user-info from keystore" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(cacheKey)(Left("unexpected key-store error"))
        }

        val result = doRequest(nino, cacheKey)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

    "handling getTermsAndConditionsPage" must {

      "redirect to the eligibility page if there is no session data in keystore" in {
        mockSuccessfulAuthorisation()

        val result = controller.getTermsAndConditionsPage(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "show the terms and conditions if the user is eligible" in {
        val key = "key"

        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(key)(Right(Some(eligibleStrideUserInfo)))
        }

        val result = controller.getTermsAndConditionsPage(FakeRequest().withSession("stride-user-info" → key))
        status(result) shouldBe OK
        contentAsString(result) should include("Websites you to link opens in prove")
      }

    }

  }

  private def fakeRequest(ninoP: String, key: String) =
    fakeRequestWithCSRFToken
      .withFormUrlEncodedBody("nino" → ninoP)
      .withSession("stride-user-info" -> key)
}
