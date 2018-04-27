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
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.i18n.MessagesApi
import play.api.libs.json.{JsValue, Json, Reads, Writes}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.connectors.{HelpToSaveConnector, KeyStoreConnector}
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.HtsSession
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.UserInfo._
import uk.gov.hmrc.helptosavestridefrontend.models.CreateAccountResult.AccountCreated
import uk.gov.hmrc.helptosavestridefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavestridefrontend.models.{CreateAccountResult, EnrolmentStatus, NSIUserInfo}
import uk.gov.hmrc.helptosavestridefrontend.util.NINO
import uk.gov.hmrc.helptosavestridefrontend.{AuthSupport, CSRFSupport, TestData, TestSupport}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.CacheMap

import scala.concurrent.{ExecutionContext, Future}

class StrideControllerSpec
  extends TestSupport with AuthSupport with CSRFSupport with TestData with GeneratorDrivenPropertyChecks { // scalastyle:off magic.number

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

  def mockGetEnrolmentStatus(nino: String)(result: Either[String, EnrolmentStatus]) =
    (helpToSaveConnector.getEnrolmentStatus(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  lazy val controller =
    new StrideController(mockAuthConnector,
                         helpToSaveConnector,
                         keystoreConnector,
                         fakeApplication.injector.instanceOf[FrontendAppConfig],
                         fakeApplication.injector.instanceOf[MessagesApi],
                         mockMetrics)

  "The StrideController" when {

    "getting the getEligibilityPage" must {

      "show the page when the user is authorised" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreDelete(Right(()))
        }

        val result = controller.getEligibilityPage(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("explain Help to Save to the customer")
      }

      "handle error during keystore delete after the user is authorised" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreDelete(Left("error during keystore delete"))
        }

        val result = controller.getEligibilityPage(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }
    }

    "the introduction-help-to-save page" must {

      "show the /introduction-help-to-save when there is no session in key-store" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
        }

        val result = controller.customerEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "show the you-are-eligible page if session is found in key-store and user is eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo))))
        }

        val result = controller.customerEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("Customer is eligible for a Help to Save account")
      }

      "redirect to the you-are-not-eligible page if session is found in key-store and but user is NOT eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(ineligibleStrideUserInfo))))
        }

        val result = controller.customerEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
      }

      "redirect to the account-already-exists page if session is found in key-store and but user has an account already" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(accountExistsStrideUserInfo))))
        }

        val result = controller.customerEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

    }

    "getting the you-are-not-eligible page" must {
      val ineligibleReasonCodes = List(3, 4, 5, 9)

        def ineligibleResponse(reasonCode: Int) = EligibilityCheckResponse("", 2, "", reasonCode)

      "show the /introduction-help-to-save when there is no session in key-store" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
        }

        val result = controller.customerNotEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "redirect to the you-are-eligible page if session is found in key-store and user is eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo))))
        }

        val result = controller.customerNotEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
      }

      "show the you-are-not-eligible page if session is found in key-store and but user is NOT eligible" in {
        ineligibleReasonCodes.foreach { code ⇒
          inSequence {
            mockSuccessfulAuthorisation()
            mockKeyStoreGet(Right(Some(HtsSession(ineligibleStrideUserInfo.copy(response = ineligibleResponse(code))))))
          }

          val result = controller.customerNotEligible(fakeRequestWithCSRFToken)
          status(result) shouldBe OK
          contentAsString(result) should include("Customer is not eligible for a Help to Save account")
        }
      }

      "show an error page if the session is found in key-store and the user is ineligible but the reason code cannot be parsed" in {
        forAll { code: Int ⇒
          whenever(!ineligibleReasonCodes.contains(code)) {
            inSequence {
              mockSuccessfulAuthorisation()
              mockKeyStoreGet(Right(Some(HtsSession(ineligibleStrideUserInfo.copy(response = ineligibleResponse(code))))))
            }

            val result = controller.customerNotEligible(fakeRequestWithCSRFToken)
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
          }
        }
      }

      "redirect to the account-already-exists page if session is found in key-store and but user has an account already" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(accountExistsStrideUserInfo))))
        }

        val result = controller.customerNotEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

    }

    "getting the account-already-exists page" must {

      "show the /introduction-help-to-save when there is no session in key-store" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
        }

        val result = controller.accountAlreadyExists(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "redirect to the you-are-eligible page if session is found in key-store and user is eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo))))
        }

        val result = controller.accountAlreadyExists(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
      }

      "redirect to the you-are-not-eligible page if session is found in key-store and but user is NOT eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(ineligibleStrideUserInfo))))
        }

        val result = controller.accountAlreadyExists(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
      }

      "show the account-already-exists page if session is found in key-store and but user has an account already" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(accountExistsStrideUserInfo))))
        }

        val result = controller.accountAlreadyExists(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("Customer already has an account")
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
        contentAsString(result) should include("Enter a valid National Insurance number")
      }

      "handle the case where user is not eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.Ineligible(emptyECResponse)))
          mockKeyStorePut(HtsSession(Ineligible(emptyECResponse)))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
      }

      "handle the case where user has already got account" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(Enrolled))
          mockKeyStorePut(HtsSession(AlreadyHasAccount))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

      "handle the case where user is eligible and paye-details exist" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.Eligible(eligibleECResponse)))
          mockPayeDetails(nino)(Right(nsiUserInfo))
          mockKeyStorePut(HtsSession(EligibleWithNSIUserInfo(eligibleECResponse, nsiUserInfo)))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
      }

      "handle the errors during eligibility check" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Left("unexpected error"))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }

      "handle the errors during retrieve user session info" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.Eligible(eligibleECResponse)))
          mockPayeDetails(nino)(Left("unexpected error"))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }

      "handle the errors when retrieving user session info from keystore" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Left("unexpected key-store error"))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }

      "return an Internal Server Error when the getEnrolmentStatus call fails" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Left("error occurred when getting enrolment status"))
        }
        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }

      "return an Internal Server Error when the updateSessionIfEnrolled returns a Left" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.Eligible(eligibleECResponse)))
          mockPayeDetails(nino)(Right(nsiUserInfo))
          mockKeyStorePut(HtsSession(EligibleWithNSIUserInfo(eligibleECResponse, nsiUserInfo)))(Left("Error occurred"))
        }
        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }
    }

    "handling getCreateAccountPage" must {

      "redirect to the eligibility page if there is no session data in keystore" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
        }

        val result = controller.getCreateAccountPage(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "show the create account page if the user is eligible and details are confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, detailsConfirmed = true))))
        }

        val result = controller.getCreateAccountPage(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("Ask customer if they understand - and accept the terms and conditions")
      }

      "redirect to the eligible page if the user is eligible and details are NOT confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo))))
        }

        val result = controller.getCreateAccountPage(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
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
        redirectLocation(result) shouldBe Some(routes.StrideController.getCreateAccountPage().url)
      }

      "handle errors in case of updating keystore with detailsConfirmed flag" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo))))
          mockKeyStorePut(HtsSession(eligibleStrideUserInfo, detailsConfirmed = true))(Left("unexpected error during put"))
        }

        val result = controller.handleDetailsConfirmed(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }

      "redirect the user when they are not logged in" in {
        mockAuthFail()
        val result = controller.handleDetailsConfirmed(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/stride/sign-in?successURL=http%3A%2F%2F%2Fhelp-to-save%2Fdigitally-excluded%2Fdetails-confirmed&origin=help-to-save-stride-frontend")
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

      "redirect to the eligible page if session data found in keystore but details are not confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, detailsConfirmed = false))))
        }

        val result = controller.createAccount(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
      }

      "show the account created page if session data found and details are confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, detailsConfirmed = true))))
          mockBEConnectorCreateAccount(nsiUserInfo)(Right(AccountCreated))
        }

        val result = controller.createAccount(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getAccountCreatedPage().url)
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
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, detailsConfirmed = true))))
          mockBEConnectorCreateAccount(nsiUserInfo)(Left("error occured creating an account"))
        }

        val result = controller.createAccount(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }
    }

    "handling getAccountCreatedPage" must {

        def doRequest(): Future[Result] = controller.getAccountCreatedPage()(FakeRequest())

      "redirect to the eligibility page if there is no session data in keystore" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(None))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "redirect the not eligible page if the session data indicates ineligibility" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(ineligibleStrideUserInfo))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
      }

      "redirect the account created page if the session data indicates ineligibility" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(accountExistsStrideUserInfo))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

      "redirect to the eligible page if the user is eligible and the details have ntt been confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, detailsConfirmed = false))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
      }

      "write an already has account status to keystore and then show the account created page" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, detailsConfirmed = true))))
          mockKeyStorePut(HtsSession(accountExistsStrideUserInfo))(Right(()))
        }

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("account has been created")
      }

      "show an error page if the keystore write fails" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockKeyStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, detailsConfirmed = true))))
          mockKeyStorePut(HtsSession(accountExistsStrideUserInfo))(Left(""))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }

    }

    "handling getErrorPage" must {

      "show the error page" in {
        mockSuccessfulAuthorisation()

        val result = controller.getErrorPage()(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR

      }

    }
  }

}

