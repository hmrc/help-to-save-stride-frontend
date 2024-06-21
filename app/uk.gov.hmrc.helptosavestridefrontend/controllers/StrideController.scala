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

import java.time.Clock

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.Inject
import play.api.i18n.I18nSupport
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavestridefrontend.audit.{HTSAuditor, ManualAccountCreationSelected, PersonalInformationDisplayedToOperator}
import uk.gov.hmrc.helptosavestridefrontend.auth.StrideAuth
import uk.gov.hmrc.helptosavestridefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavestridefrontend.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosavestridefrontend.forms.{ApplicantDetailsForm, ApplicantDetailsValidation, GiveNINOForm}
import uk.gov.hmrc.helptosavestridefrontend.models.CreateAccountResult.{AccountAlreadyExists, AccountCreated}
import uk.gov.hmrc.helptosavestridefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavestridefrontend.models.RoleType._
import uk.gov.hmrc.helptosavestridefrontend.models.SessionEligibilityCheckResult.AlreadyHasAccount
import uk.gov.hmrc.helptosavestridefrontend.models._
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResult, IneligibilityReason}
import uk.gov.hmrc.helptosavestridefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavestridefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, toFuture}
import uk.gov.hmrc.helptosavestridefrontend.views.html._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class StrideController @Inject() (
  val authConnector: AuthConnector,
  val helpToSaveConnector: HelpToSaveConnector,
  val sessionStore: SessionStore,
  val auditor: HTSAuditor,
  mcc: MessagesControllerComponents,
  errorHandler: ErrorHandler,
  getEligibilityView: get_eligibility_page,
  customerNotEligibleView: customer_not_eligible,
  accountAlreadyExistsView: account_already_exists,
  customerEligibleView: customer_eligible,
  enterCustomerDetailsView: enter_customer_details,
  createAccountView: create_account,
  accountCreatedView: account_created,
  applicationCancelledView: application_cancelled
)(implicit
  val frontendAppConfig: FrontendAppConfig,
  applicantDetailsValidation: ApplicantDetailsValidation,
  clock: Clock,
  ec: ExecutionContext
) extends StrideFrontendController(frontendAppConfig, mcc, errorHandler) with StrideAuth with I18nSupport with Logging
    with SessionBehaviour {

  def getEligibilityPage: Action[AnyContent] =
    authorisedFromStride { implicit request => roleType =>
      sessionStore.delete.fold(
        error => {
          logger.warn(error)
          SeeOther(routes.StrideController.getErrorPage().url)
        },
        _ => Ok(getEligibilityView(GiveNINOForm.giveNinoForm))
      )
    }(routes.StrideController.getEligibilityPage())

  private def checkIfAlreadyEnrolled(nino: String, roleType: RoleType)(
    ifNotEnrolled: => Future[Result]
  )(implicit hc: HeaderCarrier): Future[Result] = { // scalastyle:ignore
    def updateSessionIfEnrolled(enrolmentStatus: EnrolmentStatus, roleType: RoleType)(implicit
      hc: HeaderCarrier
    ): EitherT[Future, String, Unit] = enrolmentStatus match {
      case Enrolled =>
        roleType match {
          case Standard(_) =>
            for {
              nsiUserInfo    <- helpToSaveConnector.getNSIUserInfo(nino)
              accountDetails <- EitherT.liftF[Future, String, Option[AccountDetails]](getAccountDetails(nino))
              _ <- sessionStore.store(
                     HtsStandardSession(
                       AlreadyHasAccount,
                       nsiUserInfo,
                       accountNumber = accountDetails.map(_.accountNumber)
                     )
                   )
            } yield ()

          case Secure(_) =>
            for {
              accountDetails <- EitherT.liftF[Future, String, Option[AccountDetails]](getAccountDetails(nino))
              _ <-
                sessionStore.store(
                  HtsSecureSession(nino, AlreadyHasAccount, None, accountNumber = accountDetails.map(_.accountNumber))
                )
            } yield ()
        }

      case NotEnrolled => EitherT.pure[Future, String](())
    }

    val result =
      for {
        status <- helpToSaveConnector.getEnrolmentStatus(nino)
        _      <- updateSessionIfEnrolled(status, roleType)
      } yield status

    result
      .fold[Future[Result]](
        { e =>
          logger.warn(e)
          SeeOther(routes.StrideController.getErrorPage().url)
        },
        {
          case Enrolled    => toFuture(SeeOther(routes.StrideController.accountAlreadyExists().url))
          case NotEnrolled => ifNotEnrolled
        }
      )
      .flatMap(identity)
  }

  private def getAccountDetails(nino: String)(implicit hc: HeaderCarrier): Future[Option[AccountDetails]] =
    helpToSaveConnector
      .getAccount(nino)
      .fold(
        { e =>
          logger.warn(s"Could not get account details: $e")
          None
        },
        Some(_)
      )

  def checkEligibilityAndGetPersonalInfo: Action[AnyContent] =
    authorisedFromStride { implicit request => roleType =>
      def updateSession(
        eligibility: EligibilityCheckResult,
        nino: String,
        roleType: RoleType
      ): EitherT[Future, String, Unit] = {
        lazy val getAccountDetailsResult: EitherT[Future, String, Option[AccountDetails]] =
          EitherT.liftF(eligibility match {
            case EligibilityCheckResult.AlreadyHasAccount(_) => getAccountDetails(nino)
            case _                                           => Future.successful(None)
          })

        roleType match {
          case Standard(_) =>
            for {
              nsiUserInfo    <- helpToSaveConnector.getNSIUserInfo(nino)
              accountDetails <- getAccountDetailsResult
              _ <- sessionStore.store(
                     HtsStandardSession(
                       SessionEligibilityCheckResult.fromEligibilityCheckResult(eligibility),
                       nsiUserInfo,
                       accountNumber = accountDetails.map(_.accountNumber)
                     )
                   )
            } yield ()

          case Secure(_) =>
            for {
              accountDetails <- getAccountDetailsResult
              _ <- sessionStore.store(
                     HtsSecureSession(
                       nino,
                       SessionEligibilityCheckResult.fromEligibilityCheckResult(eligibility),
                       None,
                       accountDetails.map(_.accountNumber)
                     )
                   )
            } yield ()
        }
      }

      checkSession(roleType)(
        GiveNINOForm.giveNinoForm
          .bindFromRequest()
          .fold(
            withErrors => Ok(getEligibilityView(withErrors)),
            form =>
              checkIfAlreadyEnrolled(form.nino, roleType) {
                val result = for {
                  eligibility <- helpToSaveConnector.getEligibility(form.nino)
                  _           <- updateSession(eligibility, form.nino, roleType)
                } yield eligibility

                result.fold(
                  error => {
                    logger.warn(s"error during retrieving eligibility result and paye-personal-info, error: $error")
                    SeeOther(routes.StrideController.getErrorPage().url)
                  },
                  {
                    case EligibilityCheckResult.Eligible(_) => SeeOther(routes.StrideController.customerEligible().url)
                    case EligibilityCheckResult.Ineligible(_) =>
                      SeeOther(routes.StrideController.customerNotEligible().url)
                    case EligibilityCheckResult.AlreadyHasAccount(_) =>
                      SeeOther(routes.StrideController.accountAlreadyExists().url)
                  }
                )
              }
          )
      )
    }(routes.StrideController.checkEligibilityAndGetPersonalInfo())

  def customerNotEligible: Action[AnyContent] =
    authorisedFromStrideWithDetails { implicit request => implicit operatorDetails => roleType =>
      checkSession(roleType)(
        SeeOther(routes.StrideController.getEligibilityPage().url),
        whenIneligible = { (ineligible, nsiUserInfo, _) =>
          IneligibilityReason
            .fromIneligible(ineligible)
            .fold {
              logger.warn(s"Could not parse ineligiblity reason: $ineligible")
              SeeOther(routes.StrideController.getErrorPage().url)
            } { reason =>
              // do TxM Auditing
              val piDisplayedToOperator = PersonalInformationDisplayed(
                nsiUserInfo.nino,
                nsiUserInfo.forename + " " + nsiUserInfo.surname,
                None,
                List.empty[String]
              )
              auditor.sendEvent(
                PersonalInformationDisplayedToOperator(piDisplayedToOperator, operatorDetails, request.path),
                nsiUserInfo.nino
              )

              Ok(
                customerNotEligibleView(
                  reason,
                  Some(nsiUserInfo.forename -> nsiUserInfo.surname),
                  nsiUserInfo.nino,
                  None
                )
              )
            }
        },
        whenIneligibleSecure = { (ineligible, nino, nsiUserInfo, _) =>
          IneligibilityReason
            .fromIneligible(ineligible)
            .fold {
              logger.warn(s"Could not parse ineligibility reason: $ineligible")
              SeeOther(routes.StrideController.getErrorPage().url)
            } { reason =>
              val form = nsiUserInfo.fold(ApplicantDetailsForm.applicantDetailsForm)(ApplicantDetailsForm.apply)
              Ok(customerNotEligibleView(reason, None, nino, Some(form)))
            }
        }
      )
    }(routes.StrideController.customerNotEligible())

  def allowManualAccountCreation(): Action[AnyContent] =
    authorisedFromStrideWithDetails { implicit request => operatorDetails => roleType =>
      def storeSessionAndContinue(sessionToStore: HtsSession, nino: NINO): Future[Result] =
        sessionStore
          .store(sessionToStore)
          .fold(
            { e =>
              logger.warn(s"Could not write session to mongo: $e")
              SeeOther(routes.StrideController.getErrorPage().url)
            },
            { _ =>
              auditor.sendEvent(ManualAccountCreationSelected(nino, request.path, operatorDetails), nino)
              SeeOther(routes.StrideController.createAccount().url)
            }
          )

      checkSession(roleType)(
        SeeOther(routes.StrideController.getEligibilityPage().url),
        whenIneligible = { (ineligible, nSIUserInfo, _) =>
          storeSessionAndContinue(
            HtsStandardSession(ineligible.copy(manualCreationAllowed = true), nSIUserInfo),
            nSIUserInfo.nino
          )
        },
        whenIneligibleSecure = { (ineligible, nino, _, _) =>
          ApplicantDetailsForm.applicantDetailsForm
            .bindFromRequest()
            .fold(
              withErrors =>
                IneligibilityReason
                  .fromIneligible(ineligible)
                  .fold {
                    logger.warn(s"Could not parse ineligibility reason: $ineligible")
                    SeeOther(routes.StrideController.getErrorPage().url)
                  } { reason =>
                    Ok(customerNotEligibleView(reason, None, nino, Some(withErrors)))
                  },
              details =>
                storeSessionAndContinue(
                  HtsSecureSession(
                    nino,
                    ineligible.copy(manualCreationAllowed = true),
                    Some(NSIPayload(details, nino)),
                    None
                  ),
                  nino
                )
            )
        }
      )
    }(routes.StrideController.allowManualAccountCreation())

  def accountAlreadyExists: Action[AnyContent] =
    authorisedFromStride { implicit request => roleType =>
      checkSession(roleType)(
        SeeOther(routes.StrideController.getEligibilityPage().url),
        whenAlreadyHasAccount = (_, accountNumber) => Ok(accountAlreadyExistsView(accountNumber))
      )
    }(routes.StrideController.accountAlreadyExists())

  def customerEligible: Action[AnyContent] =
    authorisedFromStrideWithDetails { implicit request => operatorDetails => roleType =>
      checkSession(roleType)(
        SeeOther(routes.StrideController.getEligibilityPage().url),
        {
          // whenEligible
          (_, _, nsiUserInfo, _) =>
            // do TxM Auditing
            val contactDetails = nsiUserInfo.contactDetails
            val piDisplayedToOperator = PersonalInformationDisplayed(
              nsiUserInfo.nino,
              nsiUserInfo.forename + " " + nsiUserInfo.surname,
              Some(nsiUserInfo.dateOfBirth),
              List(
                contactDetails.address1,
                contactDetails.address2,
                contactDetails.address3.getOrElse(""),
                contactDetails.address4.getOrElse(""),
                contactDetails.address5.getOrElse(""),
                contactDetails.postcode
              )
            )
            auditor.sendEvent(
              PersonalInformationDisplayedToOperator(piDisplayedToOperator, operatorDetails, request.path),
              nsiUserInfo.nino
            )

            Ok(customerEligibleView(nsiUserInfo))
        },
        {
          // whenEligibleSecure
          (_, nino, nsiPayload, _) =>
            val form = nsiPayload.fold(ApplicantDetailsForm.applicantDetailsForm)(ApplicantDetailsForm.apply)
            Ok(enterCustomerDetailsView(nino, form))
        }
      )
    }(routes.StrideController.customerEligible())

  def customerEligibleSubmit: Action[AnyContent] =
    authorisedFromStride { implicit request => roleType =>
      def storeSessionAndContinue(session: HtsSession): Future[Result] =
        sessionStore
          .store(session)
          .fold(
            error => {
              logger.warn(error)
              SeeOther(routes.StrideController.getErrorPage().url)
            },
            _ => SeeOther(routes.StrideController.getCreateAccountPage().url)
          )

      checkSession(roleType)(
        SeeOther(routes.StrideController.getEligibilityPage().url), // when eligible
        (eligible, _, nsiUserInfo, _) =>
          storeSessionAndContinue(HtsStandardSession(eligible, nsiUserInfo, detailsConfirmed = true)),
        { // when eligible secure
          case (eligible, nino, _, _) =>
            ApplicantDetailsForm.applicantDetailsForm
              .bindFromRequest()
              .fold(
                withErrors => Ok(enterCustomerDetailsView(nino, withErrors)),
                details =>
                  storeSessionAndContinue(HtsSecureSession(nino, eligible, Some(NSIPayload(details, nino)), None))
              )
        }
      )
    }(routes.StrideController.customerEligible())

  def getCreateAccountPage: Action[AnyContent] =
    authorisedFromStride { implicit request => roleType =>
      checkSession(roleType)(
        SeeOther(routes.StrideController.getEligibilityPage().url),
        whenEligible = (eligible, detailsConfirmed, _, _) =>
          if (!detailsConfirmed) {
            SeeOther(routes.StrideController.customerEligible().url)
          } else {
            Ok(createAccountView(None, None, Some(eligible)))
          },
        whenEligibleSecure = (eligible, _, nsiPayload, _) =>
          nsiPayload.fold(SeeOther(routes.StrideController.customerEligible().url)) { details =>
            Ok(createAccountView(Some(details), Some(routes.StrideController.customerEligible().url), Some(eligible)))
          },
        whenIneligible = { (ineligible, _, _) =>
          if (ineligible.manualCreationAllowed) {
            Ok(createAccountView(None, None, None))
          } else {
            SeeOther(routes.StrideController.customerNotEligible().url)
          }
        },
        whenIneligibleSecure = { (ineligible, _, nsiPayload, _) =>
          if (ineligible.manualCreationAllowed) {
            nsiPayload.fold(SeeOther(routes.StrideController.customerNotEligible().url)) { details =>
              Ok(createAccountView(Some(details), Some(routes.StrideController.customerNotEligible().url), None))
            }
          } else {
            SeeOther(routes.StrideController.customerNotEligible().url)
          }
        }
      )
    }(routes.StrideController.getCreateAccountPage())

  def createAccount: Action[AnyContent] =
    authorisedFromStride { implicit request => roleType =>
      checkSession(roleType)(
        SeeOther(routes.StrideController.getEligibilityPage().url),
        whenEligible = { (eligible, detailsConfirmed, nsiUserInfo, _) =>
          if (!detailsConfirmed) {
            SeeOther(routes.StrideController.customerEligible().url)
          } else {
            createAccountAndUpdateSession(
              roleType,
              nsiUserInfo,
              eligible,
              detailsConfirmed,
              eligible.response.reasonCode,
              "Stride"
            )
          }
        },
        whenEligibleSecure = { case (eligible, _, payload, _) =>
          payload.fold[Future[Result]](SeeOther(routes.StrideController.customerEligible().url)) { info =>
            createAccountAndUpdateSession(
              roleType,
              info,
              eligible,
              detailsConfirmed = true,
              eligible.response.reasonCode,
              "Stride"
            )
          }
        },
        whenIneligible = { (ineligible, nSIUserInfo, _) =>
          if (ineligible.manualCreationAllowed) {
            // send the ineligibility reasonCode to the BE for manual account creation
            createAccountAndUpdateSession(
              roleType,
              nSIUserInfo,
              ineligible,
              false,
              ineligible.response.reasonCode,
              "Stride-Manual"
            )
          } else {
            SeeOther(routes.StrideController.customerNotEligible().url)
          }
        },
        whenIneligibleSecure = { (ineligible, _, nsiUserInfo, _) =>
          if (ineligible.manualCreationAllowed) {
            nsiUserInfo.fold[Future[Result]](SeeOther(routes.StrideController.customerNotEligible().url)) { details =>
              createAccountAndUpdateSession(
                roleType,
                details,
                ineligible,
                false,
                ineligible.response.reasonCode,
                "Stride-Manual"
              )
            }
          } else {
            SeeOther(routes.StrideController.customerNotEligible().url)
          }

        }
      )
    }(routes.StrideController.createAccount())

  private def userDetailsManuallyEntered(roleType: RoleType): Boolean = roleType match {
    case Standard(_) => false
    case Secure(_)   => true
  }

  private def createAccountAndUpdateSession(
    roleType: RoleType,
    nsiUserInfo: NSIPayload,
    eligibilityResult: SessionEligibilityCheckResult,
    detailsConfirmed: Boolean,
    reasonCode: Int,
    source: String
  )(implicit hc: HeaderCarrier) = {
    val result = for {
      createAccountResult <-
        helpToSaveConnector.createAccount(
          CreateAccountRequest(nsiUserInfo, reasonCode, source, userDetailsManuallyEntered(roleType))
        )
      sessionToStore <- createAccountResult match {
                          case AccountCreated(accountNumber) =>
                            roleType match {
                              case Standard(_) =>
                                EitherT.pure(
                                  HtsStandardSession(
                                    eligibilityResult,
                                    nsiUserInfo,
                                    detailsConfirmed,
                                    Some(accountNumber)
                                  )
                                )
                              case Secure(_) =>
                                EitherT.pure(
                                  HtsSecureSession(
                                    nsiUserInfo.nino,
                                    eligibilityResult,
                                    Some(nsiUserInfo),
                                    Some(accountNumber)
                                  )
                                )
                            }
                          case AccountAlreadyExists =>
                            roleType match {
                              case Standard(_) =>
                                EitherT
                                  .liftF(getAccountDetails(nsiUserInfo.nino))
                                  .map(a =>
                                    HtsStandardSession(
                                      AlreadyHasAccount,
                                      nsiUserInfo,
                                      detailsConfirmed,
                                      a.map(_.accountNumber)
                                    )
                                  )
                              case Secure(_) =>
                                EitherT
                                  .liftF(getAccountDetails(nsiUserInfo.nino))
                                  .map(a =>
                                    HtsSecureSession(
                                      nsiUserInfo.nino,
                                      AlreadyHasAccount,
                                      Some(nsiUserInfo),
                                      a.map(_.accountNumber)
                                    )
                                  )
                            }
                        }
      _ <- sessionStore.store(sessionToStore)
    } yield createAccountResult

    result.fold[Result](
      error => {
        logger.warn(s"error during create account and update session, error: $error")
        SeeOther(routes.StrideController.getErrorPage().url)
      },
      {
        case AccountCreated(_)    => SeeOther(routes.StrideController.getAccountCreatedPage().url)
        case AccountAlreadyExists => SeeOther(routes.StrideController.accountAlreadyExists().url)
      }
    )
  }

  def getAccountCreatedPage: Action[AnyContent] =
    authorisedFromStride { implicit request => roleType => // scalastyle:ignore

      def updateSessionAfterAccountCreate(updatedSession: HtsSession, mayBeAccountNumber: Option[String]) =
        mayBeAccountNumber.fold[Future[Result]] {
          logger.warn("expecting previously stored account number in the session, but not found")
          SeeOther(routes.StrideController.getErrorPage().url)
        } { accountNumber =>
          sessionStore
            .store(updatedSession)
            .fold(
              { e =>
                logger.warn(s"Could not write session to mongo dueto: $e")
                SeeOther(routes.StrideController.getErrorPage().url)
              },
              _ =>
                Ok(accountCreatedView(accountNumber))
            )
        }

      checkSession(roleType)(
        SeeOther(routes.StrideController.getEligibilityPage().url),
        whenEligible = { (_, detailsConfirmed, nsiPayload, maybeAccountNumber) =>
          if (!detailsConfirmed) {
            SeeOther(routes.StrideController.customerEligible().url)
          } else {
            updateSessionAfterAccountCreate(
              HtsStandardSession(
                SessionEligibilityCheckResult.AlreadyHasAccount,
                nsiPayload,
                detailsConfirmed,
                maybeAccountNumber
              ),
              maybeAccountNumber
            )
          }
        },
        whenEligibleSecure = { (_, nino, nsiPayload, maybeAccountNumber) =>
          nsiPayload.fold[Future[Result]](SeeOther(routes.StrideController.customerEligible().url)) { payload =>
            updateSessionAfterAccountCreate(
              HtsSecureSession(
                nino,
                SessionEligibilityCheckResult.AlreadyHasAccount,
                Some(payload),
                maybeAccountNumber
              ),
              maybeAccountNumber
            )
          }

        },
        whenIneligible = { (ineligible, nsiPayload, maybeAccountNumber) =>
          if (!ineligible.manualCreationAllowed) {
            SeeOther(routes.StrideController.customerNotEligible().url)
          } else {
            updateSessionAfterAccountCreate(
              HtsStandardSession(
                SessionEligibilityCheckResult.AlreadyHasAccount,
                nsiPayload,
                false,
                maybeAccountNumber
              ),
              maybeAccountNumber
            )
          }
        },
        whenIneligibleSecure = { (ineligible, nino, nsiPayload, maybeAccountNumber) =>
          nsiPayload.fold[Future[Result]](SeeOther(routes.StrideController.customerNotEligible().url)) { payload =>
            if (!ineligible.manualCreationAllowed) {
              SeeOther(routes.StrideController.customerNotEligible().url)
            } else {
              updateSessionAfterAccountCreate(
                HtsSecureSession(
                  nino,
                  SessionEligibilityCheckResult.AlreadyHasAccount,
                  Some(payload),
                  maybeAccountNumber
                ),
                maybeAccountNumber
              )
            }
          }

        }
      )
    }(routes.StrideController.getAccountCreatedPage())

  def getApplicationCancelledPage: Action[AnyContent] =
    authorisedFromStride { implicit request => roleType =>
      Ok(applicationCancelledView())
    }(routes.StrideController.getApplicationCancelledPage())

  def getErrorPage: Action[AnyContent] =
    authorisedFromStride { implicit request => roleType =>
      internalServerError()
    }(routes.StrideController.getErrorPage())

}
