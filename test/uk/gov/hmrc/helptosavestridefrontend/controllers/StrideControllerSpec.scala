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
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.i18n.MessagesApi
import play.api.libs.json.{Reads, Writes}
import play.api.mvc._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavestridefrontend.audit.{HTSAuditor, HTSEvent, ManualAccountCreationSelected, PersonalInformationDisplayedToOperator}
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosavestridefrontend.models.HtsSession
import uk.gov.hmrc.helptosavestridefrontend.models.SessionEligibilityCheckResult._
import uk.gov.hmrc.helptosavestridefrontend.models.CreateAccountResult.AccountCreated
import uk.gov.hmrc.helptosavestridefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavestridefrontend.models._
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavestridefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavestridefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavestridefrontend.util.NINO
import uk.gov.hmrc.helptosavestridefrontend.{AuthSupport, CSRFSupport, TestData, TestSupport}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class StrideControllerSpec
  extends TestSupport with AuthSupport with CSRFSupport with TestData with GeneratorDrivenPropertyChecks { // scalastyle:off magic.number

  val helpToSaveConnector = mock[HelpToSaveConnector]

  val sessionStore = mock[SessionStore]

  val mockAuditor = mock[HTSAuditor]

  val ninoEndoded = "QUUxMjM0NTZD"

  val emptyECResponse = EligibilityCheckResponse("No tax credit record found for user's NINO", 2, "", -1)
  val eligibleECResponse = EligibilityCheckResponse("eligible", 1, "tax credits", 7)

  val accountExistsResponseECR = EligibilityCheckResponse("account exists", 3, "account exists", 7)

  def mockEligibility(nino: NINO)(result: Either[String, EligibilityCheckResult]) =
    (helpToSaveConnector.getEligibility(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockPayeDetails(nino: NINO)(result: Either[String, NSIPayload]) =
    (helpToSaveConnector.getNSIUserInfo(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockSessionStoreGet(result: Either[String, Option[HtsSession]]) =
    (sessionStore.get(_: Reads[HtsSession], _: HeaderCarrier))
      .expects(*, *)
      .returning(EitherT.fromEither[Future](result))

  def mockSessionStoreInsert(htsSession: HtsSession)(result: Either[String, Unit]): Unit =
    (sessionStore.store(_: HtsSession)(_: Writes[HtsSession], _: HeaderCarrier))
      .expects(htsSession, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockSessionStoreDelete(result: Either[String, Unit]): Unit =
    (sessionStore.delete(_: HeaderCarrier))
      .expects(*)
      .returning(EitherT.fromEither[Future](result))

  def mockCreateAccount(createAccountRequest: CreateAccountRequest)(result: Either[String, CreateAccountResult]) =
    (helpToSaveConnector.createAccount(_: CreateAccountRequest)(_: HeaderCarrier, _: ExecutionContext))
      .expects(createAccountRequest, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockGetEnrolmentStatus(nino: String)(result: Either[String, EnrolmentStatus]) =
    (helpToSaveConnector.getEnrolmentStatus(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  def mockAudit(event: HTSEvent, nino: NINO) =
    (mockAuditor.sendEvent(_: HTSEvent, _: NINO)(_: ExecutionContext))
      .expects(event, nino, *)
      .returning(())

  lazy val controller =
    new StrideController(mockAuthConnector,
                         helpToSaveConnector,
                         sessionStore,
                         mockAuditor,
                         fakeApplication.injector.instanceOf[FrontendAppConfig],
                         fakeApplication.injector.instanceOf[MessagesApi])

  "The StrideController" when {

    "getting the getEligibilityPage" must {

      "show the page when the user is authorised" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreDelete(Right(()))
        }

        val result = controller.getEligibilityPage(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("explain Help to Save to the customer")
      }

      "handle error during mongo session delete after the user is authorised" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreDelete(Left("error during session delete"))
        }

        val result = controller.getEligibilityPage(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }
    }

    "the check-eligibility page" must {

      "show the /check-eligibility when there is no session in mongo" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(None))
        }

        val result = controller.customerEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "show the you-are-eligible page if session is found in mongo and user is eligible" in {
        val auditEvent = PersonalInformationDisplayedToOperator(
          PersonalInformationDisplayed(
            "AE123456C",
            "A Smith",
            Some(nsiUserInfo.dateOfBirth),
            List(contactDetails.address1,
                 contactDetails.address2,
                 contactDetails.address3.getOrElse(""),
                 contactDetails.address4.getOrElse(""),
                 contactDetails.address5.getOrElse(""),
                 contactDetails.postcode
            )),
          OperatorDetails(List("hts helpdesk advisor"), Some("PID"), "name", "email"), "/")

        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo))))
          mockAudit(auditEvent, "AE123456C")
        }

        val result = controller.customerEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("Customer is eligible for a Help to Save account")
      }

      "redirect to the you-are-not-eligible page if session is found in mongo and but user is NOT eligible" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsSession(ineligibleStrideUserInfo, nsiUserInfo))))
        }

        val result = controller.customerEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
      }

      "redirect to the account-already-exists page if session is found in mongo and but user has an account already" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsSession(accountExistsStrideUserInfo, nsiUserInfo))))
        }

        val result = controller.customerEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

    }

    "getting the you-are-not-eligible page" must {
      val ineligibleReasonCodes = List(3, 4, 5, 9)

        def ineligibleResponse(reasonCode: Int) = EligibilityCheckResponse("", 2, "", reasonCode)

      "show the /check-eligibility when there is no session in mongo" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(None))
        }

        val result = controller.customerNotEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "redirect to the you-are-eligible page if session is found in mongo and user is eligible" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo))))
        }

        val result = controller.customerNotEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
      }

      "show the you-are-not-eligible page if session is found in mongo and but user is NOT eligible" in {
        ineligibleReasonCodes.foreach { code ⇒
          inSequence {
            mockSuccessfulAuthorisationWithDetails()
            mockSessionStoreGet(Right(Some(HtsSession(ineligibleStrideUserInfo.copy(response = ineligibleResponse(code)), nsiUserInfo))))
            mockAudit(PersonalInformationDisplayedToOperator(PersonalInformationDisplayed("AE123456C", "A Smith", None, List.empty[String]), OperatorDetails(List("hts helpdesk advisor"), Some("PID"), "name", "email"), "/"), "AE123456C")
          }

          val result = controller.customerNotEligible(fakeRequestWithCSRFToken)
          status(result) shouldBe OK
          contentAsString(result) should include("Customer is not eligible for a Help to Save account")
        }
      }

      "show an error page if the session is found in mongo and the user is ineligible but the reason code cannot be parsed" in {
        forAll { code: Int ⇒
          whenever(!ineligibleReasonCodes.contains(code)) {
            inSequence {
              mockSuccessfulAuthorisationWithDetails()
              mockSessionStoreGet(Right(Some(HtsSession(ineligibleStrideUserInfo.copy(response = ineligibleResponse(code)), nsiUserInfo))))
            }

            val result = controller.customerNotEligible(fakeRequestWithCSRFToken)
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
          }
        }
      }

      "redirect to the account-already-exists page if session is found in mongo and but user has an account already" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsSession(accountExistsStrideUserInfo, nsiUserInfo))))
        }

        val result = controller.customerNotEligible(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

    }

    "getting the account-already-exists page" must {

      "show the /check-eligibility when there is no session in mongo" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
        }

        val result = controller.accountAlreadyExists(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "redirect to the you-are-eligible page if session is found in mongo and user is eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo))))
        }

        val result = controller.accountAlreadyExists(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
      }

      "redirect to the you-are-not-eligible page if session is found in mongo and but user is NOT eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(ineligibleStrideUserInfo, nsiUserInfo))))
        }

        val result = controller.accountAlreadyExists(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
      }

      "show the account-already-exists page if session is found in mongo and but user has an account already" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(accountExistsStrideUserInfo, nsiUserInfo))))
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
          mockSessionStoreGet(Right(None))
        }

        val result = doRequest("in-valid-nino")
        status(result) shouldBe OK
        contentAsString(result) should include("Enter a valid National Insurance number")
      }

      "handle the case where user is not eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.Ineligible(emptyECResponse)))
          mockPayeDetails(nino)(Right(nsiUserInfo))
          mockSessionStoreInsert(HtsSession(Ineligible(emptyECResponse, false), nsiUserInfo))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
      }

      "handle the case where user has already got account" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(Enrolled))
          mockPayeDetails(nino)(Right(nsiUserInfo))
          mockSessionStoreInsert(HtsSession(AlreadyHasAccount, nsiUserInfo))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

      "handle the case where user is eligible and paye-details exist" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.Eligible(eligibleECResponse)))
          mockPayeDetails(nino)(Right(nsiUserInfo))
          mockSessionStoreInsert(HtsSession(Eligible(eligibleECResponse), nsiUserInfo))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
      }

      "handle the errors during eligibility check" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
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
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.Eligible(eligibleECResponse)))
          mockPayeDetails(nino)(Left("unexpected error"))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }

      "handle the errors when retrieving user session info from mongo" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Left("unexpected mongo error"))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }

      "return an Internal Server Error when the getEnrolmentStatus call fails" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Left("error occurred when getting enrolment status"))
        }
        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }

      "return an Internal Server Error when the updateSessionIfEnrolled returns a Left" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.Eligible(eligibleECResponse)))
          mockPayeDetails(nino)(Right(nsiUserInfo))
          mockSessionStoreInsert(HtsSession(Eligible(eligibleECResponse), nsiUserInfo))(Left("Error occurred"))
        }
        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }
    }

    "handling getCreateAccountPage" must {

      "redirect to the eligibility page if there is no session data in mongo" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
        }

        val result = controller.getCreateAccountPage(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "show the create account page if the user is eligible and details are confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo, detailsConfirmed = true))))
        }

        val result = controller.getCreateAccountPage(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("Ask customer if they understand - and accept the terms and conditions")
      }

      "redirect to the eligible page if the user is eligible and details are NOT confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo))))
        }

        val result = controller.getCreateAccountPage(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
      }

    }

    "handling detailsConfirmed" must {

      "redirect to the eligibility page if there is no session data in mongo" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
        }

        val result = controller.handleDetailsConfirmed(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "update the detailsConfirmed flag to true in mongo and show the terms and conditions" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo))))
          mockSessionStoreInsert(HtsSession(eligibleStrideUserInfo, nsiUserInfo, detailsConfirmed = true))(Right(()))
        }

        val result = controller.handleDetailsConfirmed(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getCreateAccountPage().url)
      }

      "handle errors in case of updating mongo session with detailsConfirmed flag" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo))))
          mockSessionStoreInsert(HtsSession(eligibleStrideUserInfo, nsiUserInfo, detailsConfirmed = true))(Left("unexpected error during put"))
        }

        val result = controller.handleDetailsConfirmed(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }

      "redirect the user when they are not logged in" in {
        mockAuthFail()
        val result = controller.handleDetailsConfirmed(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some("/stride/sign-in?successURL=http%3A%2F%2F%2Fhelp-to-save%2Fhmrc-internal%2Fdetails-confirmed&origin=help-to-save-stride-frontend")
      }

    }

    "handling createAccount" must {

      "redirect to the eligibility page if there is no session data in mongo" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
        }

        val result = controller.createAccount(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "redirect to the eligible page if session data found in mongo but details are not confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo))))
        }

        val result = controller.createAccount(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
      }

      "show the account created page if session data found and details are confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo, detailsConfirmed = true))))
          mockCreateAccount(CreateAccountRequest(nsiUserInfo, eligibleStrideUserInfo.response.reasonCode, "Stride"))(Right(AccountCreated("123456789")))
          mockSessionStoreInsert(HtsSession(eligibleStrideUserInfo, nsiUserInfo, true, Some("123456789")))(Right(()))
        }

        val result = controller.createAccount(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getAccountCreatedPage().url)
      }

      "show the account already exists page when the applicant is already enrolled into HTS" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(accountExistsStrideUserInfo, nsiUserInfo))))
        }

        val result = controller.createAccount(FakeRequest())
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

      "return an Internal Server Error when the back-end returns a status other than 201 or 409" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo, detailsConfirmed = true))))
          mockCreateAccount(CreateAccountRequest(nsiUserInfo, eligibleStrideUserInfo.response.reasonCode, "Stride"))(Left("error occured creating an account"))
        }

        val result = controller.createAccount(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }

      "handle manual account creation requests when there is userInfo passed in" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(ineligibleManualOverrideStrideUserInfo, nsiUserInfo))))
          mockCreateAccount(CreateAccountRequest(nsiUserInfo, ineligibleManualOverrideStrideUserInfo.response.reasonCode, "Stride-Manual"))(Right(AccountCreated("123456789")))
          mockSessionStoreInsert(HtsSession(ineligibleManualOverrideStrideUserInfo, nsiUserInfo, false, Some("123456789")))(Right(()))
        }

        val result = controller.createAccount(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getAccountCreatedPage().url)
      }

      "show an error page if the mongo write fails after account has been created successfully" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo, detailsConfirmed = true))))
          mockCreateAccount(CreateAccountRequest(nsiUserInfo, eligibleStrideUserInfo.response.reasonCode, "Stride"))(Right(AccountCreated("123456789")))
          mockSessionStoreInsert(HtsSession(eligibleStrideUserInfo, nsiUserInfo, true, Some("123456789")))(Left(""))
        }

        val result = controller.createAccount(FakeRequest())
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }
    }

    "handling allowManualAccountCreation" must {

      "return 200 and redirect to the create account page if retrieval of user info is successful" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsSession(ineligibleManualOverrideStrideUserInfo, nsiUserInfo))))
          mockSessionStoreInsert(HtsSession(ineligibleManualOverrideStrideUserInfo, nsiUserInfo))(Right(()))
          mockAudit(ManualAccountCreationSelected("AE123456C", "/", OperatorDetails(List("hts helpdesk advisor"), Some("PID"), "name", "email")), "AE123456C")
        }

        val result = controller.allowManualAccountCreation()(fakeRequestWithCSRFToken)
        status(result) shouldBe OK
        contentAsString(result) should include("Create an account")
      }

      "redirect to the error page when retrieval of user info fails" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsSession(ineligibleManualOverrideStrideUserInfo, nsiUserInfo))))
          mockSessionStoreInsert(HtsSession(ineligibleManualOverrideStrideUserInfo, nsiUserInfo))(Left(""))
        }

        val result = controller.allowManualAccountCreation()(fakeRequestWithCSRFToken)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }
    }

    "handling getAccountCreatedPage" must {

        def doRequest(): Future[Result] = controller.getAccountCreatedPage()(FakeRequest())

      "redirect to the eligibility page if there is no session data in mongo" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "redirect to the not eligible page if the session data indicates ineligibility" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(ineligibleStrideUserInfo, nsiUserInfo))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
      }

      "redirect the account created page if the session data indicates ineligibility" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(accountExistsStrideUserInfo, nsiUserInfo))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

      "redirect to the eligible page if the user is eligible and the details have not been confirmed" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo))))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
      }

      "write an 'already has account' status to mongo and then show the account created page" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo, detailsConfirmed = true, Some("123456789")))))
          mockSessionStoreInsert(HtsSession(accountExistsStrideUserInfo, nsiUserInfo, true, Some("123456789")))(Right(()))
        }

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("Help to Save account created")
      }

      "show an error page if the mongo write fails" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(eligibleStrideUserInfo, nsiUserInfo, detailsConfirmed = true))))
          mockSessionStoreInsert(HtsSession(accountExistsStrideUserInfo, nsiUserInfo, true))(Left(""))
        }

        val result = doRequest()
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }

      "redirect the account created page if the session data indicates ineligibility but the stride operator creates account manually" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsSession(ineligibleManualOverrideStrideUserInfo, nsiUserInfo, false, Some("123456789")))))
          mockSessionStoreInsert(HtsSession(accountExistsStrideUserInfo, nsiUserInfo, false, Some("123456789")))(Right(()))

        }

        val result = doRequest()
        status(result) shouldBe OK
        contentAsString(result) should include("Help to Save account created")
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

