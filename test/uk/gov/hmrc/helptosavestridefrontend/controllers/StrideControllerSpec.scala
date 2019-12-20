/*
 * Copyright 2019 HM Revenue & Customs
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

import java.time.{Clock, Instant, ZoneId}

import cats.data.EitherT
import cats.instances.future._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.{Reads, Writes}
import play.api.mvc._
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavestridefrontend._
import uk.gov.hmrc.helptosavestridefrontend.audit.{HTSAuditor, HTSEvent, ManualAccountCreationSelected, PersonalInformationDisplayedToOperator}
import uk.gov.hmrc.helptosavestridefrontend.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetailsValidation
import uk.gov.hmrc.helptosavestridefrontend.models.CreateAccountResult.{AccountAlreadyExists, AccountCreated}
import uk.gov.hmrc.helptosavestridefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavestridefrontend.models.SessionEligibilityCheckResult._
import uk.gov.hmrc.helptosavestridefrontend.models.{HtsStandardSession, _}
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavestridefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavestridefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavestridefrontend.util.NINO
import uk.gov.hmrc.helptosavestridefrontend.views.ApplicantDetailsForm.Ids
import uk.gov.hmrc.helptosavestridefrontend.views.html._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class StrideControllerSpec
  extends TestSupport with AuthSupport with CSRFSupport with TestData with ScalaCheckDrivenPropertyChecks { // scalastyle:off magic.number

  implicit val clock: Clock = Clock.fixed(Instant.EPOCH, ZoneId.of("Z"))

  private val fakeRequest = FakeRequest("GET", "/")

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

  def mockGetAccount(nino: String)(result: Either[String, AccountDetails]) =
    (helpToSaveConnector.getAccount(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(nino, *, *)
      .returning(EitherT.fromEither[Future](result))

  implicit lazy val applicantDetailsValidation: ApplicantDetailsValidation =
    fakeApplication.injector.instanceOf[ApplicantDetailsValidation]

  lazy val controller =
    new StrideController(mockAuthConnector,
                         helpToSaveConnector,
                         sessionStore,
                         mockAuditor,
                         testMcc,
                         errorHandler,
                         injector.instanceOf[get_eligibility_page],
                         injector.instanceOf[customer_not_eligible],
                         injector.instanceOf[account_already_exists],
                         injector.instanceOf[customer_eligible],
                         injector.instanceOf[enter_customer_details],
                         injector.instanceOf[create_account],
                         injector.instanceOf[account_created],
                         injector.instanceOf[application_cancelled]
    )

  val validEnterDetailsFormBody = Map(
    Ids.forename → nsiUserInfo.forename,
    Ids.surname → nsiUserInfo.surname,
    Ids.dobDay → nsiUserInfo.dateOfBirth.getDayOfMonth.toString,
    Ids.dobMonth → nsiUserInfo.dateOfBirth.getMonthValue.toString,
    Ids.dobYear → nsiUserInfo.dateOfBirth.getYear.toString,
    Ids.address1 → nsiUserInfo.contactDetails.address1,
    Ids.address2 → nsiUserInfo.contactDetails.address2,
    Ids.address3 → nsiUserInfo.contactDetails.address3.getOrElse(""),
    Ids.address4 → nsiUserInfo.contactDetails.address4.getOrElse(""),
    Ids.address5 → nsiUserInfo.contactDetails.address5.getOrElse(""),
    Ids.postcode → nsiUserInfo.contactDetails.postcode,
    Ids.countryCode → "GB"
  )

  "The StrideController" when {

    "getting the getEligibilityPage" must {

      "show the page when the user is authorised" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreDelete(Right(()))
        }

        val result = controller.getEligibilityPage(request)
        status(result) shouldBe OK
        contentAsString(result) should include("explain Help to Save to the customer")
      }

      "handle error during mongo session delete after the user is authorised" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreDelete(Left("error during session delete"))
        }

        val result = controller.getEligibilityPage(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }
    }

    "the customer eligible page" must {

      "show the /check-eligibility when there is no session in mongo" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(None))
        }

        val result = controller.customerEligible(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "show customer-eligible page if session is found in mongo and user is eligible" in {
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
          OperatorDetails(List("hts helpdesk advisor", "hts_helpdesk_advisor"), Some("PID"), "name", "email"), "/")

        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo))))
          mockAudit(auditEvent, "AE123456C")
        }

        val result = controller.customerEligible(request)
        status(result) shouldBe OK
        contentAsString(result) should include("Customer is eligible for an account")
        contentAsString(result) should not include ("enter their details")
      }

      "show the enter details page if session is found in mongo and user is eligible and the role type is secure" in {
        inSequence {
          mockSuccessfulSecureAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsSecureSession(nino, eligibleResult, None, None))))
        }

        val result = controller.customerEligible(request)
        status(result) shouldBe OK
        contentAsString(result) should include("Customer is eligible")
        contentAsString(result) should include("enter their details")
      }

      "redirect to the you-are-not-eligible page if session is found in mongo and but user is NOT eligible" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsStandardSession(ineligibleEligibilityResult, nsiUserInfo))))
        }

        val result = controller.customerEligible(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
      }

      "redirect to the account-already-exists page if session is found in mongo and user has an account already" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsStandardSession(accountExistsEligibilityResult, nsiUserInfo))))
        }

        val result = controller.customerEligible(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

      "show the enter-customer-details page if the stride operator has a Secure role type and the caller is eligible" in {
        inSequence {
          mockSuccessfulSecureAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsSecureSession("AE123456C", eligibleResult, None))))
        }

        val result = controller.customerEligible(request)
        status(result) shouldBe OK
        contentAsString(result) should include("enter their details")
      }

      "show the enter-customer-details page with the customer's details if the stride operator has a Secure role type and the caller is eligible " +
        "and there are customer details in the session" in {
          inSequence {
            mockSuccessfulSecureAuthorisationWithDetails()
            mockSessionStoreGet(Right(Some(HtsSecureSession("AE123456C", eligibleResult, Some(nsiUserInfo)))))
          }

          val result = controller.customerEligible(request)
          status(result) shouldBe OK
          contentAsString(result) should include("enter their details")
          contentAsString(result) should include(nsiUserInfo.forename)
        }
    }

    "getting the customer-not-eligible page" must {
      val ineligibleReasonCodes = List(3, 4, 5, 9)

        def ineligibleResponse(reasonCode: Int) = EligibilityCheckResponse("", 2, "", reasonCode)

      "show the /check-eligibility when there is no session in mongo" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(None))
        }

        val result = controller.customerNotEligible(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "redirect to the you-are-eligible page if session is found in mongo and user is eligible" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo))))
        }

        val result = controller.customerNotEligible(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
      }

      "show the you-are-not-eligible page if session is found in mongo and but user is NOT eligible" in {
        ineligibleReasonCodes.foreach { code ⇒
          inSequence {
            mockSuccessfulAuthorisationWithDetails()
            mockSessionStoreGet(Right(Some(HtsStandardSession(ineligibleEligibilityResult.copy(response = ineligibleResponse(code)), nsiUserInfo))))
            mockAudit(PersonalInformationDisplayedToOperator(PersonalInformationDisplayed("AE123456C", "A Smith", None, List.empty[String]),
                                                             OperatorDetails(List("hts helpdesk advisor", "hts_helpdesk_advisor"), Some("PID"), "name", "email"), "/"), "AE123456C")
          }

          val result = controller.customerNotEligible(request)
          status(result) shouldBe OK
          contentAsString(result) should include("Customer is not eligible for a Help to Save account")
          contentAsString(result) should include("Name:")
          contentAsString(result) should include("Create account manually")
        }
      }

      "show the customer-are-not-eligible page if session is found in mongo and but user is NOT eligible and the role type is secure" in {
        ineligibleReasonCodes.foreach { code ⇒
          inSequence {
            mockSuccessfulSecureAuthorisationWithDetails()
            mockSessionStoreGet(Right(Some(HtsSecureSession(nino, ineligibleEligibilityResult.copy(response = ineligibleResponse(code)), None, None))))
          }

          val result = controller.customerNotEligible(request)
          status(result) shouldBe OK

          val content = contentAsString(result)
          content should include("Customer is not eligible for a Help to Save account")
          content should not include ("Name:")
          content should include ("Create account manually")
          content should include ("Personal details")
        }
      }

      "show the customer-are-not-eligible page with customer details if session is found in mongo and but user " +
        "is NOT eligible and the role type is secure and there are customer details in the session" in {
          ineligibleReasonCodes.foreach { code ⇒
            inSequence {
              mockSuccessfulSecureAuthorisationWithDetails()
              mockSessionStoreGet(Right(Some(HtsSecureSession(nino, ineligibleEligibilityResult.copy(response = ineligibleResponse(code)), Some(nsiUserInfo), None))))
            }

            val result = controller.customerNotEligible(request)
            status(result) shouldBe OK

            val content = contentAsString(result)
            content should include("Customer is not eligible for a Help to Save account")
            content should not include ("Name:")
            content should include("Create account manually")
            content should include("Personal details")
            content should include(nsiUserInfo.contactDetails.address1)
          }
        }

      "show an error page if the session is found in mongo and the user is ineligible but the reason code cannot be parsed" in {
        forAll { code: Int ⇒
          whenever(!ineligibleReasonCodes.contains(code)) {
            inSequence {
              mockSuccessfulAuthorisationWithDetails()
              mockSessionStoreGet(Right(Some(HtsStandardSession(ineligibleEligibilityResult.copy(response = ineligibleResponse(code)), nsiUserInfo))))
            }

            val result = controller.customerNotEligible(request)
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
          }
        }
      }

      "show an error page if the session is found in mongo and the user is ineligible but the reason code cannot be parsed " +
        "and the role type is secure" in {
          forAll { code: Int ⇒
            whenever(!ineligibleReasonCodes.contains(code)) {
              inSequence {
                mockSuccessfulSecureAuthorisationWithDetails()
                mockSessionStoreGet(Right(Some(HtsSecureSession(nino, ineligibleEligibilityResult.copy(response = ineligibleResponse(code)), None, None))))
              }

              val result = controller.customerNotEligible(request)
              status(result) shouldBe SEE_OTHER
              redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
            }
          }
        }

      "redirect to the account-already-exists page if session is found in mongo and but user has an account already" in {
        inSequence {
          mockSuccessfulAuthorisationWithDetails()
          mockSessionStoreGet(Right(Some(HtsStandardSession(accountExistsEligibilityResult, nsiUserInfo))))
        }

        val result = controller.customerNotEligible(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

    }

    "handling allowManualAccountCreation" when {

      "the role type is standard" must {

        "redirect to the create account page if retrieval of user info is successful" in {
          inSequence {
            mockSuccessfulAuthorisationWithDetails()
            mockSessionStoreGet(Right(Some(HtsStandardSession(ineligibleManualOverrideEligibilityResult, nsiUserInfo))))
            mockSessionStoreInsert(HtsStandardSession(ineligibleManualOverrideEligibilityResult, nsiUserInfo))(Right(()))
            mockAudit(ManualAccountCreationSelected("AE123456C", "/", OperatorDetails(List("hts helpdesk advisor", "hts_helpdesk_advisor"),
                                                                                      Some("PID"), "name", "email")), "AE123456C")
          }

          val result = controller.allowManualAccountCreation()(request)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getCreateAccountPage().url)
        }

        "redirect to the error page when retrieval of user info fails" in {
          inSequence {
            mockSuccessfulAuthorisationWithDetails()
            mockSessionStoreGet(Right(Some(HtsStandardSession(ineligibleManualOverrideEligibilityResult, nsiUserInfo))))
            mockSessionStoreInsert(HtsStandardSession(ineligibleManualOverrideEligibilityResult, nsiUserInfo))(Left(""))
          }

          val result = controller.allowManualAccountCreation()(request)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
        }

      }

      "the role type is secure" must {

        val expectedSession =
          HtsSecureSession(
            nino,
            ineligibleEligibilityResult.copy(manualCreationAllowed = true),
            Some(nsiUserInfo.copy(contactDetails = nsiUserInfo.contactDetails.copy(phoneNumber = None))),
            None)

        "show the form with errors if the form data is invalid" in {
          inSequence {
            mockSuccessfulSecureAuthorisationWithDetails()
            mockSessionStoreGet(Right(Some(HtsSecureSession(nino, ineligibleEligibilityResult, None, None))))
          }

          val result = controller.allowManualAccountCreation()(request)
          status(result) shouldBe OK

          val content = contentAsString(result)

          content should include("There is a problem")
          content should include("Customer is not eligible for an account")
          content should include ("Personal details")

        }

        "write the data to mongo and redirect to the create account page if the data is valid" in {
          inSequence {
            mockSuccessfulSecureAuthorisationWithDetails()
            mockSessionStoreGet(Right(Some(HtsSecureSession(nino, ineligibleEligibilityResult, None, None))))
            mockSessionStoreInsert(expectedSession)(Right(()))
            mockAudit(ManualAccountCreationSelected("AE123456C", "/",
              OperatorDetails(List("hts helpdesk advisor secure", "hts_helpdesk_advisor_secure"),
                              Some("PID"), "name", "email")), "AE123456C")
          }

          val result = csrfAddToken(controller.allowManualAccountCreation())(fakeRequest.withFormUrlEncodedBody(validEnterDetailsFormBody.toList: _*))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getCreateAccountPage().url)
        }

        "return a 500 if there is an error writing to mongo" in {
          inSequence {
            mockSuccessfulSecureAuthorisationWithDetails()
            mockSessionStoreGet(Right(Some(HtsSecureSession(nino, ineligibleEligibilityResult, None, None))))
            mockSessionStoreInsert(expectedSession)(Left("uh oh"))
          }

          val result = csrfAddToken(controller.allowManualAccountCreation())(fakeRequest.withFormUrlEncodedBody(validEnterDetailsFormBody.toList: _*))
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
        }

      }

    }

    "getting the account-already-exists page" must {

      "show the /check-eligibility when there is no session in mongo" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
        }

        val result = controller.accountAlreadyExists(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
      }

      "redirect to the you-are-eligible page if session is found in mongo and user is eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo))))
        }

        val result = controller.accountAlreadyExists(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
      }

      "redirect to the you-are-not-eligible page if session is found in mongo and but user is NOT eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsStandardSession(ineligibleEligibilityResult, nsiUserInfo))))
        }

        val result = controller.accountAlreadyExists(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
      }

      "show the account-already-exists page if session is found in mongo and but user has an account already" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(Some(HtsStandardSession(accountExistsEligibilityResult, nsiUserInfo))))
        }

        val result = controller.accountAlreadyExists(request)
        status(result) shouldBe OK
        contentAsString(result) should include("Customer already has an account")
      }
    }

    "checking the eligibility and retrieving paye details" must {

        def doRequest(nino: String) =
          controller.checkEligibilityAndGetPersonalInfo(FakeRequest().withFormUrlEncodedBody("nino" → nino).withCSRFToken)

      "handle the forms with invalid input" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
        }

        val result = doRequest("in-valid-nino")
        status(result) shouldBe OK
        contentAsString(result) should include("Enter a valid National Insurance number")
      }

      "handle the case where the eligibility check indicates that the user is not eligible" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.Ineligible(emptyECResponse)))
          mockPayeDetails(nino)(Right(nsiUserInfo))
          mockSessionStoreInsert(HtsStandardSession(Ineligible(emptyECResponse, false), nsiUserInfo))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
      }

      "handle the case where the enrolment store indicates that user has already got account" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(Enrolled))
          mockPayeDetails(nino)(Right(nsiUserInfo))
          mockGetAccount(nino)(Right(AccountDetails("account")))
          mockSessionStoreInsert(HtsStandardSession(AlreadyHasAccount, nsiUserInfo, accountNumber = Some("account")))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

      "handle the case where the enrolment store indicates that user has already got account and the role type is secure" in {
        inSequence {
          mockSuccessfulSecureAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(Enrolled))
          mockGetAccount(nino)(Right(AccountDetails("account")))
          mockSessionStoreInsert(HtsSecureSession(nino, AlreadyHasAccount, None, accountNumber = Some("account")))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

      "handle the case where the enrolment store indicates that user has already got account but the account reference number cannot be retrieved" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(Enrolled))
          mockPayeDetails(nino)(Right(nsiUserInfo))
          mockGetAccount(nino)(Left("oh no"))
          mockSessionStoreInsert(HtsStandardSession(AlreadyHasAccount, nsiUserInfo, accountNumber = None))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

      "handle the case where the enrolment store indicates that user has already got account but the account reference number cannot be retrieved " +
        "and the role type is secure" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(None))
            mockGetEnrolmentStatus(nino)(Right(Enrolled))
            mockGetAccount(nino)(Left("oh no"))
            mockSessionStoreInsert(HtsSecureSession(nino, AlreadyHasAccount, None, None))(Right(()))
          }

          val result = doRequest(nino)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
        }

      "handle the case where the eligibility check indicates that the user has an account" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.AlreadyHasAccount(emptyECResponse)))
          mockPayeDetails(nino)(Right(nsiUserInfo))
          mockGetAccount(nino)(Right(AccountDetails("account")))
          mockSessionStoreInsert(HtsStandardSession(AlreadyHasAccount, nsiUserInfo, accountNumber = Some("account")))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

      "handle the case where the eligibility check indicates that the user has an account and the role type is secure" in {
        inSequence {
          mockSuccessfulSecureAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.AlreadyHasAccount(emptyECResponse)))
          mockGetAccount(nino)(Right(AccountDetails("account")))
          mockSessionStoreInsert(HtsSecureSession(nino, AlreadyHasAccount, None, Some("account")))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

      "handle the case where the eligibility check indicates that the user has an account but the account number cannot be retrieved" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.AlreadyHasAccount(emptyECResponse)))
          mockPayeDetails(nino)(Right(nsiUserInfo))
          mockGetAccount(nino)(Left("uh oh"))
          mockSessionStoreInsert(HtsStandardSession(AlreadyHasAccount, nsiUserInfo, accountNumber = None))(Right(()))
        }

        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
      }

      "handle the case where the eligibility check indicates that the user has an account but the account number cannot be retrieved and the role type is secure" in {
        inSequence {
          mockSuccessfulSecureAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.AlreadyHasAccount(emptyECResponse)))
          mockGetAccount(nino)(Left("uh oh"))
          mockSessionStoreInsert(HtsSecureSession(nino, AlreadyHasAccount, None, None))(Right(()))
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
          mockSessionStoreInsert(HtsStandardSession(Eligible(eligibleECResponse), nsiUserInfo))(Right(()))
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

      "return an Internal Server Error when the session cannot be stored" in {
        inSequence {
          mockSuccessfulAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.Eligible(eligibleECResponse)))
          mockPayeDetails(nino)(Right(nsiUserInfo))
          mockSessionStoreInsert(HtsStandardSession(Eligible(eligibleECResponse), nsiUserInfo))(Left("Error occurred"))
        }
        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }

      "return an Internal Server Error when the session cannot be stored and the role type is secure" in {
        inSequence {
          mockSuccessfulSecureAuthorisation()
          mockSessionStoreGet(Right(None))
          mockGetEnrolmentStatus(nino)(Right(NotEnrolled))
          mockEligibility(nino)(Right(EligibilityCheckResult.Eligible(eligibleECResponse)))
          mockSessionStoreInsert(HtsSecureSession(nino, Eligible(eligibleECResponse), None, None))(Left("Error occurred"))
        }
        val result = doRequest(nino)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
      }
    }

    "handling getCreateAccountPage" when {

      "the role type is standard" must {

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
            mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo, detailsConfirmed = true))))
          }

          val result = controller.getCreateAccountPage(request)

          status(result) shouldBe OK
          contentAsString(result) should include("Ask the customer if they understand and agree to the terms and conditions")
        }

        "redirect to the eligible page if the user is eligible and details are NOT confirmed" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo))))
          }

          val result = controller.getCreateAccountPage(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
        }

        "show the create account page if the user is ineligible and manual account creation has been selected" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockSessionStoreGet(Right(Some(HtsStandardSession(ineligibleManualOverrideEligibilityResult, nsiUserInfo))))
          }

          val result = controller.getCreateAccountPage(request)
          status(result) shouldBe OK
          contentAsString(result) should include("Ask the customer if they understand and agree to the terms and conditions")
        }

        "redirect to the ineligible page if the user is ineligible and manual account creation has not been selected" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockSessionStoreGet(Right(Some(HtsStandardSession(ineligibleEligibilityResult, nsiUserInfo))))
          }

          val result = controller.getCreateAccountPage(request)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
        }

      }

      "the role type is secure" must {

        "redirect to the eligible page if there is no session" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(None))
          }

          val result = controller.getCreateAccountPage(request)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
        }

        "redirect to the eligible page if there is no customer data in the session and the customer is eligible" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(Some(HtsSecureSession(nino, eligibleResult, None, None))))
          }

          val result = controller.getCreateAccountPage(request)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
        }

        "show the create account page with customer details for the call handler to check if the customer is eligible" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(Some(HtsSecureSession(nino, eligibleResult, Some(nsiUserInfo), None))))
          }

          val result = controller.getCreateAccountPage(request)
          status(result) shouldBe OK
          contentAsString(result) should include(nsiUserInfo.forename)
          contentAsString(result) should include("Ask the customer if they understand and agree to the terms and conditions")
        }

        "redirect to the not eligible page if there is no customer data in the session and the customer is not eligible" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(Some(HtsSecureSession(nino, ineligibleEligibilityResult.copy(manualCreationAllowed = true), None, None))))
          }

          val result = controller.getCreateAccountPage(request)
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
        }

        "redirect to the not eligible page if there is customer data in the session and the customer is " +
          "not eligible but manual account creation has not been enabled" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(HtsSecureSession(nino, ineligibleEligibilityResult.copy(manualCreationAllowed = false), Some(nsiUserInfo), None))))
            }

            val result = controller.getCreateAccountPage(request)
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
          }

        "show the create account page with customer details for the call handler to check if the customer is ineligible " +
          "but manual account creation has been selected" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(HtsSecureSession(nino, ineligibleEligibilityResult.copy(manualCreationAllowed = true), Some(nsiUserInfo), None))))
            }

            val result = controller.getCreateAccountPage(request)
            status(result) shouldBe OK
            contentAsString(result) should include(nsiUserInfo.forename)
            contentAsString(result) should include("Ask the customer if they understand and agree to the terms and conditions")
          }

      }

    }

    "handling customer-eligible-submit" when {

      "the role type is standard" must {

        "redirect to the eligibility page if there is no session data in mongo" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockSessionStoreGet(Right(None))
          }

          val result = controller.customerEligibleSubmit(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
        }

        "update the detailsConfirmed flag to true in mongo and show the terms and conditions" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo))))
            mockSessionStoreInsert(HtsStandardSession(eligibleResult, nsiUserInfo, detailsConfirmed = true))(Right(()))
          }

          val result = controller.customerEligibleSubmit(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getCreateAccountPage().url)
        }

        "handle errors in case of updating mongo session with detailsConfirmed flag" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo))))
            mockSessionStoreInsert(HtsStandardSession(eligibleResult, nsiUserInfo, detailsConfirmed = true))(Left("unexpected error during put"))
          }

          val result = controller.customerEligibleSubmit(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
        }

      }

      "the role type is secure" must {

        val expectedSession =
          HtsSecureSession(nino, eligibleResult, Some(nsiUserInfo.copy(contactDetails = nsiUserInfo.contactDetails.copy(phoneNumber = None))), None)

        "show the form with errors if the form data is invalid" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(Some(HtsSecureSession(nino, eligibleResult, None, None))))
          }

          val result = controller.customerEligibleSubmit(request)
          status(result) shouldBe OK

          val content = contentAsString(result)

          content should include("There is a problem")
          content should include("Customer is eligible for an account")
          content should include ("enter their details")

        }

        "write the data to mongo and redirect to the create account page if the data is valid" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(Some(HtsSecureSession(nino, eligibleResult, None, None))))
            mockSessionStoreInsert(expectedSession)(Right(()))
          }

          val result = csrfAddToken(controller.customerEligibleSubmit)(fakeRequest.withFormUrlEncodedBody(validEnterDetailsFormBody.toList: _*))

          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getCreateAccountPage().url)
        }

        "return a 500 if there is an error writing to mongo" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(Some(HtsSecureSession(nino, eligibleResult, None, None))))
            mockSessionStoreInsert(expectedSession)(Left("uh oh"))
          }

          val result = csrfAddToken(controller.customerEligibleSubmit)(fakeRequest.withFormUrlEncodedBody(validEnterDetailsFormBody.toList: _*))
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
        }

      }

      "the role type is either standard or secure" must {

        "redirect the user when they are not logged in" in {
          mockAuthFail()

          val result = controller.customerEligibleSubmit(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some("/stride/sign-in?successURL=http%3A%2F%2Flocalhost%2Fhelp-to-save%2Fhmrc-internal%2Fcustomer-eligible&origin=help-to-save-stride-frontend")
        }

      }

    }

    "handling createAccount" when {

      "the roleType is standard" must {

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
            mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo))))
          }

          val result = controller.createAccount(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
        }

        "show the account created page if session data found and details are confirmed" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo, detailsConfirmed = true))))
            mockCreateAccount(CreateAccountRequest(nsiUserInfo, eligibleResult.response.reasonCode, "Stride", false))(Right(AccountCreated("123456789")))
            mockSessionStoreInsert(HtsStandardSession(eligibleResult, nsiUserInfo, true, Some("123456789")))(Right(()))
          }

          val result = controller.createAccount(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getAccountCreatedPage().url)
        }

        "redirect to the account already exists page" when {

          "the session indicates that the applicant is already enrolled into HTS" in {
            inSequence {
              mockSuccessfulAuthorisation()
              mockSessionStoreGet(Right(Some(HtsStandardSession(accountExistsEligibilityResult, nsiUserInfo))))
            }

            val result = controller.createAccount(FakeRequest())
            redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
          }

          "NS&I indicate that the account already exists" in {
            val accountNumber = "account"
            inSequence {
              mockSuccessfulAuthorisation()
              mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo, detailsConfirmed = true))))
              mockCreateAccount(CreateAccountRequest(nsiUserInfo, eligibleResult.response.reasonCode, "Stride", false))(Right(AccountAlreadyExists))
              mockGetAccount(nsiUserInfo.nino)(Right(AccountDetails(accountNumber)))
              mockSessionStoreInsert(HtsStandardSession(AlreadyHasAccount, nsiUserInfo, true, Some(accountNumber)))(Right(()))
            }

            val result = controller.createAccount(FakeRequest())
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
          }

          "NS&I indicate that the account already exists and the account number cannot be retrieved" in {
            inSequence {
              mockSuccessfulAuthorisation()
              mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo, detailsConfirmed = true))))
              mockCreateAccount(CreateAccountRequest(nsiUserInfo, eligibleResult.response.reasonCode, "Stride", false))(Right(AccountAlreadyExists))
              mockGetAccount(nsiUserInfo.nino)(Left("oh no"))
              mockSessionStoreInsert(HtsStandardSession(AlreadyHasAccount, nsiUserInfo, true, None))(Right(()))
            }

            val result = controller.createAccount(FakeRequest())
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
          }
        }

        "return an Internal Server Error when the back-end returns a status other than 201 or 409" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo, detailsConfirmed = true))))
            mockCreateAccount(CreateAccountRequest(nsiUserInfo, eligibleResult.response.reasonCode, "Stride", false))(Left("error occured creating an account"))
          }

          val result = controller.createAccount(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
        }

        "handle manual account creation requests when there is userInfo passed in" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockSessionStoreGet(Right(Some(HtsStandardSession(ineligibleManualOverrideEligibilityResult, nsiUserInfo))))
            mockCreateAccount(CreateAccountRequest(nsiUserInfo, ineligibleManualOverrideEligibilityResult.response.reasonCode, "Stride-Manual", false))(Right(AccountCreated("123456789")))
            mockSessionStoreInsert(HtsStandardSession(ineligibleManualOverrideEligibilityResult, nsiUserInfo, false, Some("123456789")))(Right(()))
          }

          val result = controller.createAccount(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getAccountCreatedPage().url)
        }

        "show an error page if the mongo write fails after account has been created successfully" in {
          inSequence {
            mockSuccessfulAuthorisation()
            mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo, detailsConfirmed = true))))
            mockCreateAccount(CreateAccountRequest(nsiUserInfo, eligibleResult.response.reasonCode, "Stride", false))(Right(AccountCreated("123456789")))
            mockSessionStoreInsert(HtsStandardSession(eligibleResult, nsiUserInfo, true, Some("123456789")))(Left(""))
          }

          val result = controller.createAccount(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
        }

        "redirect to the not eligible page" when {

          "the customer is not eligible and manual account creation has not been selected" in {
            inSequence {
              mockSuccessfulAuthorisation()
              mockSessionStoreGet(Right(Some(HtsStandardSession(ineligibleEligibilityResult, nsiUserInfo))))
            }

            val result = controller.createAccount(FakeRequest())
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
          }

        }

      }

      "the roleType is secure" must {

        val secureSession = HtsSecureSession(nino, eligibleResult, Some(nsiUserInfo), None)

        "redirect to the eligibility page if there is no session data in mongo" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(None))
          }

          val result = controller.createAccount(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
        }

        "redirect to the eligible page if session data found in mongo but there are no customer details" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(Some(secureSession.copy(nSIUserInfo = None))))
          }

          val result = controller.createAccount(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
        }

        "show the account created page if session data found and details are confirmed" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(Some(secureSession)))
            mockCreateAccount(CreateAccountRequest(nsiUserInfo, eligibleResult.response.reasonCode, "Stride", true))(Right(AccountCreated("123456789")))
            mockSessionStoreInsert(secureSession.copy(accountNumber = Some("123456789")))(Right(()))
          }

          val result = controller.createAccount(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getAccountCreatedPage().url)
        }

        "redirect to the account already exists page" when {

          "the session indicates that the applicant is already enrolled into HTS" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(HtsSecureSession(nino, accountExistsEligibilityResult, None, None))))
            }

            val result = controller.createAccount(FakeRequest())
            redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
          }

          "NS&I indicate that the account already exists" in {
            val accountNumber = "account"
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(secureSession)))
              mockCreateAccount(CreateAccountRequest(nsiUserInfo, eligibleResult.response.reasonCode, "Stride", true))(Right(AccountAlreadyExists))
              mockGetAccount(nsiUserInfo.nino)(Right(AccountDetails(accountNumber)))
              mockSessionStoreInsert(HtsSecureSession(nino, AlreadyHasAccount, Some(nsiUserInfo), Some(accountNumber)))(Right(()))
            }

            val result = controller.createAccount(FakeRequest())
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
          }

          "NS&I indicate that the account already exists and the account number cannot be retrieved" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(secureSession)))
              mockCreateAccount(CreateAccountRequest(nsiUserInfo, eligibleResult.response.reasonCode, "Stride", true))(Right(AccountAlreadyExists))
              mockGetAccount(nsiUserInfo.nino)(Left("Uh oh!"))
              mockSessionStoreInsert(HtsSecureSession(nino, AlreadyHasAccount, Some(nsiUserInfo), None))(Right(()))
            }

            val result = controller.createAccount(FakeRequest())
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
          }
        }

        "return an Internal Server Error when the back-end returns a status other than 201 or 409" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(Some(secureSession)))
            mockCreateAccount(CreateAccountRequest(nsiUserInfo, eligibleResult.response.reasonCode, "Stride", true))(Left("error ocurred creating an account"))
          }

          val result = controller.createAccount(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
        }

        "show an error page if the mongo write fails after account has been created successfully" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(Some(secureSession)))
            mockCreateAccount(CreateAccountRequest(nsiUserInfo, eligibleResult.response.reasonCode, "Stride", true))(Right(AccountCreated("123456789")))
            mockSessionStoreInsert(secureSession.copy(accountNumber = Some("123456789")))(Left(""))
          }

          val result = controller.createAccount(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
        }

        "handle manual account creation requests when there is userInfo passed in" in {
          inSequence {
            mockSuccessfulSecureAuthorisation()
            mockSessionStoreGet(Right(Some(HtsSecureSession(nino, ineligibleManualOverrideEligibilityResult, Some(nsiUserInfo)))))
            mockCreateAccount(CreateAccountRequest(nsiUserInfo, ineligibleManualOverrideEligibilityResult.response.reasonCode, "Stride-Manual", true))(Right(AccountCreated("123456789")))
            mockSessionStoreInsert(HtsSecureSession(nino, ineligibleManualOverrideEligibilityResult, Some(nsiUserInfo), Some("123456789")))(Right(()))
          }

          val result = controller.createAccount(FakeRequest())
          status(result) shouldBe SEE_OTHER
          redirectLocation(result) shouldBe Some(routes.StrideController.getAccountCreatedPage().url)
        }

        "redirect to the not eligible page" when {

          "the customer is not eligible and manual account creation has not been selected" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(HtsSecureSession(nino, ineligibleEligibilityResult, Some(nsiUserInfo)))))
            }

            val result = controller.createAccount(FakeRequest())
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
          }

          "the customer is not eligible and manual account creation has been selected but there are " +
            "not customer details" in {
              inSequence {
                mockSuccessfulSecureAuthorisation()
                mockSessionStoreGet(Right(Some(HtsSecureSession(nino, ineligibleManualOverrideEligibilityResult, None))))
              }

              val result = controller.createAccount(FakeRequest())
              status(result) shouldBe SEE_OTHER
              redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
            }
        }

      }

    }

    "handling getAccountCreatedPage" when {

        def doRequest(): Future[Result] = controller.getAccountCreatedPage()(FakeRequest())

      "the roleType is standard" when {

        "the customer is eligible" must {

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
              mockSessionStoreGet(Right(Some(HtsStandardSession(ineligibleEligibilityResult, nsiUserInfo))))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
          }

          "redirect the account created page if the session data indicates ineligibility" in {
            inSequence {
              mockSuccessfulAuthorisation()
              mockSessionStoreGet(Right(Some(HtsStandardSession(accountExistsEligibilityResult, nsiUserInfo))))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
          }

          "redirect to the eligible page if the user is eligible and the details have not been confirmed" in {
            inSequence {
              mockSuccessfulAuthorisation()
              mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo))))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
          }

          "write an 'already has account' status to mongo and then show the account created page" in {
            inSequence {
              mockSuccessfulAuthorisation()
              mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo, detailsConfirmed = true, Some("123456789")))))
              mockSessionStoreInsert(HtsStandardSession(accountExistsEligibilityResult, nsiUserInfo, true, Some("123456789")))(Right(()))
            }

            val result = doRequest()
            status(result) shouldBe OK
            contentAsString(result) should include("Help to Save account created")
          }

          "show an error page if the mongo write fails" in {
            inSequence {
              mockSuccessfulAuthorisation()
              mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo, detailsConfirmed = true, Some("123456789")))))
              mockSessionStoreInsert(HtsStandardSession(accountExistsEligibilityResult, nsiUserInfo, true, Some("123456789")))(Left(""))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
          }

          "show an error page if there is no account number in the session" in {
            inSequence {
              mockSuccessfulAuthorisation()
              mockSessionStoreGet(Right(Some(HtsStandardSession(eligibleResult, nsiUserInfo, detailsConfirmed = true))))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
          }
        }

        "the customer is ineligible" must {

          "show the account created page if the session data indicates ineligibility but the stride operator creates account manually" in {
            inSequence {
              mockSuccessfulAuthorisation()
              mockSessionStoreGet(Right(Some(HtsStandardSession(ineligibleManualOverrideEligibilityResult, nsiUserInfo, false, Some("123456789")))))
              mockSessionStoreInsert(HtsStandardSession(accountExistsEligibilityResult, nsiUserInfo, false, Some("123456789")))(Right(()))

            }

            val result = doRequest()
            status(result) shouldBe OK
            contentAsString(result) should include("Help to Save account created")
          }

          "redirect the customer ineligible page if the stride operator has not chosen to manually create an account" in {
            inSequence {
              mockSuccessfulAuthorisation()
              mockSessionStoreGet(Right(Some(HtsStandardSession(ineligibleEligibilityResult, nsiUserInfo, false, Some("123456789")))))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
          }
        }
      }

      "the roleType is secure" when {

        val accountNumber = "12345"
        val secureSession = HtsSecureSession(nino, eligibleResult, Some(nsiUserInfo), Some(accountNumber))

        "the customer is eligible" must {
          "redirect to the eligibility page if there is no session data in mongo" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(None))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.getEligibilityPage().url)
          }

          "redirect to the not eligible page if the session data indicates ineligibility" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(secureSession.copy(userInfo = ineligibleEligibilityResult))))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
          }

          "redirect the account created page if the session data indicates ineligibility" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(secureSession.copy(userInfo = accountExistsEligibilityResult))))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.accountAlreadyExists().url)
          }

          "redirect to the eligible page if the user is eligible and there are no user details" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(secureSession.copy(nSIUserInfo = None))))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.customerEligible().url)
          }

          "write an 'already has account' status to mongo and then show the account created page" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(secureSession)))
              mockSessionStoreInsert(secureSession.copy(userInfo = accountExistsEligibilityResult))(Right(()))
            }

            val result = doRequest()
            status(result) shouldBe OK
            contentAsString(result) should include("Help to Save account created")
          }

          "show an error page if the mongo write fails" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(secureSession)))
              mockSessionStoreInsert(secureSession.copy(userInfo = accountExistsEligibilityResult))(Left("Oh no!"))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
          }

          "show an error page if there is no account number" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(secureSession.copy(accountNumber = None))))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.getErrorPage().url)
          }
        }

        "the customer is ineligible" must {

          "show the account created page if the session data indicates ineligibility but the stride operator creates account manually" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(secureSession.copy(userInfo = ineligibleManualOverrideEligibilityResult))))
              mockSessionStoreInsert(secureSession.copy(userInfo = accountExistsEligibilityResult))(Right(()))
            }

            val result = doRequest()
            status(result) shouldBe OK
            contentAsString(result) should include("Help to Save account created")
          }

          "redirect the customer ineligible page if the stride operator has not chosen to manually create an account" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(secureSession.copy(userInfo = ineligibleEligibilityResult))))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
          }

          "redirect the customer ineligible page if the stride operator has chosen to manually create an account but there are no customer details" in {
            inSequence {
              mockSuccessfulSecureAuthorisation()
              mockSessionStoreGet(Right(Some(secureSession.copy(userInfo    = ineligibleManualOverrideEligibilityResult, nSIUserInfo = None))))
            }

            val result = doRequest()
            status(result) shouldBe SEE_OTHER
            redirectLocation(result) shouldBe Some(routes.StrideController.customerNotEligible().url)
          }

        }

      }

    }

    "handling getErrorPage" must {

      "show the error page" in {
        mockSuccessfulAuthorisation()

        val result = controller.getErrorPage()(FakeRequest())
        status(result) shouldBe INTERNAL_SERVER_ERROR

      }

    }

    "handling the get application cancelled page" must {

      "display the correct content" in {
        mockSuccessfulAuthorisation()

        val result = controller.getApplicationCancelledPage()(FakeRequest())
        status(result) shouldBe OK

        val content = contentAsString(result)
        content should include("Application cancelled")
        content should include("End call")
      }

    }

  }

}

