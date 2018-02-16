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
import cats.syntax.either._
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Reads, Writes}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.connectors.{HelpToSaveConnector, KeyStoreConnector}
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.HtsSession
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.UserInfo._
import uk.gov.hmrc.helptosavestridefrontend.models.CreateAccountResult.AccountCreated
import uk.gov.hmrc.helptosavestridefrontend.models.PayePersonalDetails
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavestridefrontend.util.NINO
import uk.gov.hmrc.helptosavestridefrontend.models.{CreateAccountResult, NSIUserInfo}
import uk.gov.hmrc.helptosavestridefrontend.util.{Result ⇒ _, _}
import uk.gov.hmrc.helptosavestridefrontend.{AuthSupport, CSRFSupport, TestData, TestSupport}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.{ExecutionContext, Future}

class StrideControllerSpec extends TestSupport with AuthSupport with CSRFSupport with TestData { // scalastyle:off magic.number

  val helpToSaveConnector = mock[HelpToSaveConnector]

  val keystoreConnector = mock[KeyStoreConnector]

  val ninoEndoded = "QUUxMjM0NTZD"

  val emptyECResponse = EligibilityCheckResponse("No tax credit record found for user's NINO", 2, "", -1)
  val eligibleECResponse = EligibilityCheckResponse("eligible", 1, "tax credits", 7)

  val accountExistsResponseECR = EligibilityCheckResponse("account exists", 3, "account exists", 7)

  def mockEligibility(nino: NINO)(result: Either[String, EligibilityCheckResult]) =
    (helpToSaveConnector.getEligibility(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockPayeDetails(nino: NINO)(result: Either[String, NSIUserInfo]) =
    (helpToSaveConnector.getNSIUserInfo(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockKeyStoreGet(result: Either[String, Option[HtsSession]]) =
    (keystoreConnector.get(_: Reads[HtsSession], _: HeaderCarrier, _: ExecutionContext))
      .expects(*, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockKeyStorePut(htsSession: HtsSession)(result: Either[String, Unit]): Unit =
    (keystoreConnector.put(_: HtsSession)(_: Writes[HtsSession], _: HeaderCarrier, _: ExecutionContext))
      .expects(htsSession, *, *, *)
      .returning(EitherT.fromEither[Future](result.map(_ ⇒ CacheMap("1", Map.empty[String, JsValue]))))

  def mockKeyStoreDelete(result: Either[String, Unit]): Unit =
    (keystoreConnector.delete(_: HeaderCarrier, _: ExecutionContext))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockBEConnectorCreateAccount(nSIUserInfo: NSIUserInfo)(result: Either[String, CreateAccountResult]) =
    (helpToSaveConnector.createAccount(_: NSIUserInfo)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nSIUserInfo, *, *)
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
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreDelete(Right(()))
        }

        val result = controller.getEligibilityPage(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("Help to Save - Enter National Insurance number")
      }

      "handle error during keystore delete after the user is authorised" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreDelete(Left("error during keystore delete"))
        }

        val result = controller.getEligibilityPage(fakeRequestWithCSRFToken)
        status(result) shouldBe INTERNAL_SERVER_ERROR
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

        "show the /check-eligibility-page when there is no session in key-store" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockKeyStoreGet(Right(None))
          }

          val result = doRequest(fakeRequestWithCSRFToken)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/help-to-save-stride/check-eligibility-page")
        }

        "show the you-are-eligible page if session is found in key-store and user is eligible" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo))))
          }

          val result = doRequest(fakeRequestWithCSRFToken)
          status(result) shouldBe OK
          contentAsString(result) should include("you are eligible")
        }

        "show the you-are-not-eligible page if session is found in key-store and but user is NOT eligible" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockKeyStoreGet(Right(Some(HtsSession(inEligibleStrideUserInfo))))
          }

          val result = doRequest(fakeRequestWithCSRFToken)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/help-to-save-stride/you-are-not-eligible")
        }

        "show the account-already-exists page if session is found in key-store and but user has an account already" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockKeyStoreGet(Right(Some(HtsSession(accountExistsStrideUserInfo))))
          }

          val result = doRequest(fakeRequestWithCSRFToken)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/help-to-save-stride/account-already-exists")
        }
      }

    "checking the eligibility and retrieving paye details" must {

        def doRequest(nino: String) =
          controller.checkEligibilityAndGetPersonalInfo(fakeRequestWithCSRFToken.withFormUrlEncodedBody("nino" → nino))

      "handle the forms with invalid input" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
        }

        val result = doRequest("in-valid-nino")
        status(result) shouldBe OK
        contentAsString(result) should include("invalid input, sample valid input is : AE123456C")
      }

      "handle the case where user is not eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
          mockEligibility(nino)(Right(EligibilityCheckResult.Ineligible(emptyECResponse)))
          mockKeyStorePut(HtsSession(Ineligible(emptyECResponse)))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/help-to-save-stride/you-are-not-eligible")
      }

      "handle the case where user has already got account" in {
        val accountExistsResponse = EligibilityCheckResponse("account exists", 3, "account exists", 7)

        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
          mockEligibility(nino)(Right(EligibilityCheckResult.AlreadyHasAccount(accountExistsResponseECR)))
          mockKeyStorePut(HtsSession(AlreadyHasAccount(accountExistsResponseECR)))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/help-to-save-stride/account-already-exists")
      }

      "handle the case where user is eligible and paye-details exist" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
          mockEligibility(nino)(Right(EligibilityCheckResult.Eligible(eligibleECResponse)))

          mockPayeDetails(nino)(Right(nsiUserInfo))
          mockKeyStorePut(HtsSession(EligibleWithNSIUserInfo(eligibleECResponse, nsiUserInfo)))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe OK
        contentAsString(result) should include("Help to Save - You are eligible")
        contentAsString(result) should include("you are eligible")
      }

      "handle the errors during eligibility check" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
          mockEligibility(nino)(Left("unexpected error"))
        }

        val result = doRequest(nino)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "handle the errors during retrieve user session info" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
          mockEligibility(nino)(Right(EligibilityCheckResult.Eligible(eligibleECResponse)))
          mockPayeDetails(nino)(Left("unexpected error"))
        }

        val result = doRequest(nino)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "handle the errors when retrieving user session info from keystore" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Left("unexpected key-store error"))
        }

        val result = doRequest(nino)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

    "handling getTermsAndConditionsPage" must {

      "redirect to the eligibility page if there is no session data in keystore" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
        }

        val result = controller.getTermsAndConditionsPage(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "show the terms and conditions if the user is eligible and details are confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, detailsConfirmed = true))))
        }

        val result = controller.getTermsAndConditionsPage(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("Websites you to link opens in prove")
      }

      "show the terms and conditions if the user is eligible and details are NOT confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo))))
        }

        val result = controller.getTermsAndConditionsPage(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.youAreEligible().url)
      }

    }

    "handling detailsConfirmed" must {

      "redirect to the eligibility page if there is no session data in keystore" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
        }

        val result = controller.handleDetailsConfirmed(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "update the detailsConfirmed flag to true in keystore and show the terms and conditions" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo))))
          mockKeyStorePut(HtsSession(eligibleStrideUserInfo, detailsConfirmed = true))(Right(()))
        }

        val result = controller.handleDetailsConfirmed(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getTermsAndConditionsPage().url)
      }

      "handle errors incase of updating keystore with detailsConfirmed flag" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo))))
          mockKeyStorePut(HtsSession(eligibleStrideUserInfo, detailsConfirmed = true))(Left("unexpected error during put"))
        }

        val result = controller.handleDetailsConfirmed(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

    }

    "handling createAccount" must {

      "redirect to the eligibility page if there is no session data in keystore" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
        }

        val result = controller.createAccount(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "redirect to the technical error page if session data found in keystore but details are not confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, detailsConfirmed = false))))
          mockBEConnectorCreateAccount(nsiUserInfo)(Left(""))
        }

        val result = controller.createAccount(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsString(result) should include("An account was not created due to a technical error")
      }

      "show the account created page if session data found and details are confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, detailsConfirmed = true))))
          mockBEConnectorCreateAccount(nsiUserInfo)(Right(AccountCreated))
        }

        val result = controller.createAccount(FakeRequest())
        status(result) shouldBe OK
        contentAsString(result) shouldBe routes.StrideController.getAccountCreatedPage().url
      }

      "show the account already exists page when the applicant is already enrolled into HTS" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(accountExistsStrideUserInfo))))
        }

        val result = controller.createAccount(FakeRequest())
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

      "return an Internal Server Error when the back-end returns a status other than 201 or 409" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo))))
          mockBEConnectorCreateAccount(nsiUserInfo)(Left("error occured creating an account"))
        }

        val result = controller.createAccount(FakeRequest())
        status(result) shouldBe 500
      }
    }

  }
}
