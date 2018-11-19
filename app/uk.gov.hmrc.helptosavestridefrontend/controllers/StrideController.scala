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
import com.google.inject.Inject
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavestridefrontend.audit.{HTSAuditor, ManualAccountCreationSelected, PersonalInformationDisplayedToOperator}
import uk.gov.hmrc.helptosavestridefrontend.auth.StrideAuth
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosavestridefrontend.forms.GiveNINOForm
import uk.gov.hmrc.helptosavestridefrontend.models.CreateAccountResult.{AccountAlreadyExists, AccountCreated}
import uk.gov.hmrc.helptosavestridefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavestridefrontend.models.SessionEligibilityCheckResult.AlreadyHasAccount
import uk.gov.hmrc.helptosavestridefrontend.models._
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResult, IneligibilityReason}
import uk.gov.hmrc.helptosavestridefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavestridefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavestridefrontend.views
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class StrideController @Inject() (val authConnector:       AuthConnector,
                                  val helpToSaveConnector: HelpToSaveConnector,
                                  val sessionStore:        SessionStore,
                                  val auditor:             HTSAuditor,
                                  val frontendAppConfig:   FrontendAppConfig,
                                  messageApi:              MessagesApi)(implicit val transformer: NINOLogMessageTransformer)
  extends StrideFrontendController(messageApi, frontendAppConfig) with StrideAuth with I18nSupport with Logging with SessionBehaviour {

  def getEligibilityPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    sessionStore.delete.fold(
      error ⇒ {
        logger.warn(error)
        SeeOther(routes.StrideController.getErrorPage().url)
      },
      _ ⇒ Ok(views.html.get_eligibility_page(GiveNINOForm.giveNinoForm))
    )
  }(routes.StrideController.getEligibilityPage())

  private def checkIfAlreadyEnrolled(nino: String)(ifNotEnrolled: ⇒ Future[Result])(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] = { // scalastyle:ignore
      def updateSessionIfEnrolled(enrolmentStatus: EnrolmentStatus)(implicit hc: HeaderCarrier): EitherT[Future, String, Unit] = enrolmentStatus match {
        case Enrolled ⇒
          for {
            nsiUserInfo ← helpToSaveConnector.getNSIUserInfo(nino)
            _ ← sessionStore.store(HtsSession(AlreadyHasAccount, nsiUserInfo))
          } yield ()

        case NotEnrolled ⇒ EitherT.pure[Future, String](())
      }

    val result = for {
      status ← helpToSaveConnector.getEnrolmentStatus(nino)
      _ ← updateSessionIfEnrolled(status)
    } yield status

    result.fold[Future[Result]](
      { e ⇒
        logger.warn(e)
        SeeOther(routes.StrideController.getErrorPage().url)
      }, {
        case Enrolled    ⇒ toFuture(SeeOther(routes.StrideController.accountAlreadyExists().url))
        case NotEnrolled ⇒ ifNotEnrolled
      }
    ).flatMap(identity)
  }

  def checkEligibilityAndGetPersonalInfo: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(
      GiveNINOForm.giveNinoForm.bindFromRequest().fold(
        withErrors ⇒ Ok(views.html.get_eligibility_page(withErrors)),
        form ⇒
          checkIfAlreadyEnrolled(form.nino) {
            val r = for {
              eligibility ← helpToSaveConnector.getEligibility(form.nino)
              nsiUserInfo ← helpToSaveConnector.getNSIUserInfo(form.nino)
              _ ← sessionStore.store(HtsSession(SessionEligibilityCheckResult.fromEligibilityCheckResult(eligibility), nsiUserInfo))
            } yield eligibility

            r.fold(
              error ⇒ {
                logger.warn(s"error during retrieving eligibility result and paye-personal-info, error: $error")
                SeeOther(routes.StrideController.getErrorPage().url)
              }, {
                case EligibilityCheckResult.Eligible(_) ⇒
                  SeeOther(routes.StrideController.customerEligible().url)
                case EligibilityCheckResult.Ineligible(_) ⇒
                  SeeOther(routes.StrideController.customerNotEligible().url)
                case EligibilityCheckResult.AlreadyHasAccount(_) ⇒
                  SeeOther(routes.StrideController.accountAlreadyExists().url)
              }
            )
          })
    )
  }(routes.StrideController.checkEligibilityAndGetPersonalInfo())

  def customerNotEligible: Action[AnyContent] = authorisedFromStrideWithDetails { implicit request ⇒ implicit operatorDetails ⇒
    checkSession(
      SeeOther(routes.StrideController.getEligibilityPage().url),
      whenIneligible = { (ineligible, nsiUserInfo, _) ⇒
        IneligibilityReason.fromIneligible(ineligible).fold {
          logger.warn(s"Could not parse ineligiblity reason: $ineligible")
          SeeOther(routes.StrideController.getErrorPage().url)
        } { reason ⇒
          //do TxM Auditing
          val piDisplayedToOperator = PersonalInformationDisplayed(nsiUserInfo.nino, nsiUserInfo.forename + " " + nsiUserInfo.surname, None, List.empty[String])
          auditor.sendEvent(PersonalInformationDisplayedToOperator(piDisplayedToOperator, operatorDetails, request.path), nsiUserInfo.nino)

          Ok(views.html.customer_not_eligible(reason, nsiUserInfo))
        }
      }
    )
  }(routes.StrideController.customerNotEligible())

  def accountAlreadyExists: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(
      SeeOther(routes.StrideController.getEligibilityPage().url),
      whenAlreadyHasAccount = _ ⇒ Ok(views.html.account_already_exists())
    )
  }(routes.StrideController.accountAlreadyExists())

  def customerEligible: Action[AnyContent] = authorisedFromStrideWithDetails { implicit request ⇒ operatorDetails ⇒
    checkSession(
      SeeOther(routes.StrideController.getEligibilityPage().url),
      {
        (_, _, nsiUserInfo, _) ⇒
          //do TxM Auditing
          val contactDetails = nsiUserInfo.contactDetails
          val piDisplayedToOperator = PersonalInformationDisplayed(nsiUserInfo.nino, nsiUserInfo.forename + " " + nsiUserInfo.surname,
                                                                   Some(nsiUserInfo.dateOfBirth),
                                                                   List(contactDetails.address1,
                                                                        contactDetails.address2,
                                                                        contactDetails.address3.getOrElse(""),
                                                                        contactDetails.address4.getOrElse(""),
                                                                        contactDetails.address5.getOrElse(""),
                                                                        contactDetails.postcode
            ))
          auditor.sendEvent(PersonalInformationDisplayedToOperator(piDisplayedToOperator, operatorDetails, request.path), nsiUserInfo.nino)

          Ok(views.html.customer_eligible(nsiUserInfo))
      }
    )
  }(routes.StrideController.customerEligible())

  def handleDetailsConfirmed: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(
      SeeOther(routes.StrideController.getEligibilityPage().url),
      whenEligible = (eligible, _, nsiUserInfo, _) ⇒
        sessionStore.store(HtsSession(eligible, nsiUserInfo, detailsConfirmed = true)).fold(
          error ⇒ {
            logger.warn(error)
            SeeOther(routes.StrideController.getErrorPage().url)
          },
          _ ⇒ SeeOther(routes.StrideController.getCreateAccountPage().url)
        )
    )
  }(routes.StrideController.handleDetailsConfirmed())

  def getCreateAccountPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(
      SeeOther(routes.StrideController.getEligibilityPage().url),

      whenEligible = (_, detailsConfirmed, _, _) ⇒
        if (!detailsConfirmed) {
          SeeOther(routes.StrideController.customerEligible().url)

        } else {
          Ok(views.html.create_account())
        }
    )
  }(routes.StrideController.getCreateAccountPage())

  def createAccount: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url),
                 whenEligible   = { (eligible, detailsConfirmed, nsiUserInfo, _) ⇒
        if (!detailsConfirmed) {
          SeeOther(routes.StrideController.customerEligible().url)
        } else {
          createAccountAndUpdateSession(nsiUserInfo, eligible, detailsConfirmed, eligible.response.reasonCode, "Stride")
        }
      },
                 whenIneligible = { (ineligible, nSIUserInfo, _) ⇒
        if (ineligible.manualCreationAllowed) {
          // send the ineligibility reasonCode to the BE for manual account creation
          createAccountAndUpdateSession(nSIUserInfo, ineligible, false, ineligible.response.reasonCode, "Stride-Manual")
        } else {
          SeeOther(routes.StrideController.customerNotEligible().url)
        }
      }
    )
  }(routes.StrideController.createAccount())

  private def createAccountAndUpdateSession(nsiUserInfo:       NSIPayload,
                                            eligibilityResult: SessionEligibilityCheckResult,
                                            detailsConfirmed:  Boolean,
                                            reasonCode:        Int,
                                            source:            String)(implicit hc: HeaderCarrier, request: Request[_]) = {
    val result = for {
      createAccountResult ← helpToSaveConnector.createAccount(CreateAccountRequest(nsiUserInfo, reasonCode, source))
      _ ← createAccountResult match {
        case AccountCreated(accountNumber) ⇒
          sessionStore.store(HtsSession(eligibilityResult, nsiUserInfo, detailsConfirmed, Some(accountNumber)))
        case AccountAlreadyExists ⇒ EitherT.liftF[Future, String, Unit](toFuture(()))
      }
    } yield createAccountResult

    result.fold[Result](
      error ⇒ {
        logger.warn(s"error during create account and update session, error: $error")
        SeeOther(routes.StrideController.getErrorPage().url)
      }, {
        case AccountCreated(_)    ⇒ SeeOther(routes.StrideController.getAccountCreatedPage().url)
        case AccountAlreadyExists ⇒ Ok(views.html.account_already_exists())
      }
    )
  }

  def allowManualAccountCreation(): Action[AnyContent] = authorisedFromStrideWithDetails { implicit request ⇒ operatorDetails ⇒
    if (appConfig.manualAccountCreationEnabled) {
      checkSession(SeeOther(routes.StrideController.getEligibilityPage().url),
                   whenIneligible = { (ineligible, nSIUserInfo, _) ⇒
          {
            sessionStore.store(HtsSession(ineligible.copy(manualCreationAllowed = true), nSIUserInfo)).fold({
              e ⇒
                logger.warn(s"Could not write session to mongo: $e")
                SeeOther(routes.StrideController.getErrorPage().url)
            }, { _ ⇒
              auditor.sendEvent(ManualAccountCreationSelected(nSIUserInfo.nino, request.path, operatorDetails), nSIUserInfo.nino)
              Ok(views.html.create_account())
            })
          }
        }
      )
    } else {
      Forbidden
    }
  }(routes.StrideController.allowManualAccountCreation())

  def getAccountCreatedPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒

      def updateSessionAfterAccountCreate(nSIPayload:         NSIPayload,
                                          detailsConfirmed:   Boolean,
                                          mayBeAccountNumber: Option[String]) = {
        sessionStore.store(HtsSession(SessionEligibilityCheckResult.AlreadyHasAccount, nSIPayload, detailsConfirmed, mayBeAccountNumber)).fold({
          e ⇒
            logger.warn(s"Could not write session to mongo dueto: $e")
            SeeOther(routes.StrideController.getErrorPage().url)
        }, { _ ⇒
          mayBeAccountNumber.fold(
            {
              logger.warn(s"expecting previously stored account number in the session, but not found")
              SeeOther(routes.StrideController.getErrorPage().url)
            }
          )(
              accountNumber ⇒ Ok(views.html.account_created(accountNumber))
            )
        }
        )
      }

    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url),
                 whenEligible   = { (_, detailsConfirmed, nsiUserInfo, mayBeAccountNumber) ⇒
        if (!detailsConfirmed) {
          SeeOther(routes.StrideController.customerEligible().url)
        } else {
          updateSessionAfterAccountCreate(nsiUserInfo, detailsConfirmed, mayBeAccountNumber)
        }
      },
                 whenIneligible = { (ineligible, nsiPayload, mayBeAccountNumber) ⇒
        if (!ineligible.manualCreationAllowed) {
          SeeOther(routes.StrideController.customerNotEligible().url)
        } else {
          updateSessionAfterAccountCreate(nsiPayload, false, mayBeAccountNumber)
        }
      }
    )
  }(routes.StrideController.getAccountCreatedPage())

  def getApplicationCancelledPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    Ok(views.html.application_cancelled())
  }(routes.StrideController.getApplicationCancelledPage())

  def getErrorPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    internalServerError()
  }(routes.StrideController.getErrorPage())

}
