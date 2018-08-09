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
import uk.gov.hmrc.helptosavestridefrontend.auth.StrideAuth
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.connectors.{HelpToSaveConnector, KeyStoreConnector}
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.UserInfo.{AlreadyHasAccount, EligibleWithNSIUserInfo}
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.{HtsSession, UserInfo}
import uk.gov.hmrc.helptosavestridefrontend.forms.GiveNINOForm
import uk.gov.hmrc.helptosavestridefrontend.models.CreateAccountResult.{AccountAlreadyExists, AccountCreated}
import uk.gov.hmrc.helptosavestridefrontend.models.{EnrolmentStatus, NSIUserInfo}
import uk.gov.hmrc.helptosavestridefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult.Eligible
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult, IneligibilityReason}
import uk.gov.hmrc.helptosavestridefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, NINOLogMessageTransformer, toFuture}
import uk.gov.hmrc.helptosavestridefrontend.views
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class StrideController @Inject() (val authConnector:       AuthConnector,
                                  val helpToSaveConnector: HelpToSaveConnector,
                                  val keyStoreConnector:   KeyStoreConnector,
                                  val frontendAppConfig:   FrontendAppConfig,
                                  messageApi:              MessagesApi)(implicit val transformer: NINOLogMessageTransformer)
  extends StrideFrontendController(messageApi, frontendAppConfig) with StrideAuth with I18nSupport with Logging with SessionBehaviour {

  def getEligibilityPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    keyStoreConnector.delete.fold(
      error ⇒ {
        logger.warn(error)
        SeeOther(routes.StrideController.getErrorPage().url)
      },
      _ ⇒ Ok(views.html.get_eligibility_page(GiveNINOForm.giveNinoForm))
    )
  }(routes.StrideController.getEligibilityPage())

  private def checkIfAlreadyEnrolled(nino: String)(ifNotEnrolled: ⇒ Future[Result])(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] = { // scalastyle:ignore
      def updateSessionIfEnrolled(enrolmentStatus: EnrolmentStatus)(implicit hc: HeaderCarrier): EitherT[Future, String, Unit] = enrolmentStatus match {
        case Enrolled    ⇒ keyStoreConnector.put(HtsSession(AlreadyHasAccount)).map(_ ⇒ ())
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
          checkIfAlreadyEnrolled(form.nino){
            val r = for {
              eligibility ← helpToSaveConnector.getEligibility(form.nino)
              sessionUserInfo ← getPersonalDetails(eligibility, form.nino)
              _ ← keyStoreConnector.put(HtsSession(sessionUserInfo))
            } yield sessionUserInfo

            r.fold(
              error ⇒ {
                logger.warn(s"error during retrieving eligibility result and paye-personal-info, error: $error")
                SeeOther(routes.StrideController.getErrorPage().url)
              }, {
                case UserInfo.EligibleWithNSIUserInfo(_, details) ⇒
                  SeeOther(routes.StrideController.customerEligible().url)
                case UserInfo.Ineligible(_, details, manualCreationAllowed) ⇒
                  SeeOther(routes.StrideController.customerNotEligible().url)
                case UserInfo.AlreadyHasAccount ⇒
                  SeeOther(routes.StrideController.accountAlreadyExists().url)
              }
            )
          })
    )
  }(routes.StrideController.checkEligibilityAndGetPersonalInfo())

  def customerNotEligible: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(
      SeeOther(routes.StrideController.getEligibilityPage().url),
      whenIneligible = { ineligible ⇒
        IneligibilityReason.fromIneligible(ineligible).fold{
          logger.warn(s"Could not parse ineligiblity reason: $ineligible")
          SeeOther(routes.StrideController.getErrorPage().url)
        }{ reason ⇒
          Ok(views.html.customer_not_eligible(reason))
        }
      }
    )
  }(routes.StrideController.customerNotEligible())

  def accountAlreadyExists: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(
      SeeOther(routes.StrideController.getEligibilityPage().url),
      whenAlreadyHasAccount = () ⇒ Ok(views.html.account_already_exists())
    )
  }(routes.StrideController.accountAlreadyExists())

  def customerEligible: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(
      SeeOther(routes.StrideController.getEligibilityPage().url),
      (e, _) ⇒ Ok(views.html.customer_eligible(e.nSIUserInfo))
    )
  }(routes.StrideController.customerEligible())

  def handleDetailsConfirmed: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(
      SeeOther(routes.StrideController.getEligibilityPage().url),
      whenEligible = (eligible, _) ⇒
        keyStoreConnector.put(HtsSession(eligible, detailsConfirmed = true)).fold(
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

      whenEligible = (_, detailsConfirmed) ⇒
        if (!detailsConfirmed) {
          SeeOther(routes.StrideController.customerEligible().url)

        } else {
          Ok(views.html.create_account())
        }
    )
  }(routes.StrideController.getCreateAccountPage())

  private def createAccount(nsiUserInfo: NSIUserInfo, reasonCode: Int)(implicit hc: HeaderCarrier,
                                                                       request: Request[_]): Future[Result] =
    helpToSaveConnector.createAccount(CreateAccountRequest(nsiUserInfo, reasonCode)).fold(
      error ⇒ {
        logger.warn(s"error during create account call, error: $error")
        SeeOther(routes.StrideController.getErrorPage().url)
      }, {
        case AccountCreated ⇒
          SeeOther(routes.StrideController.getAccountCreatedPage().url)
        case AccountAlreadyExists ⇒
          Ok(views.html.account_already_exists())
      })

  def createAccount: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url),
                 whenEligible = { (eligible, detailsConfirmed) ⇒
        if (!detailsConfirmed) {
          SeeOther(routes.StrideController.customerEligible().url)
        } else {
          createAccount(eligible.nSIUserInfo, eligible.response.reasonCode)
        }
      }
    )
  }(routes.StrideController.createAccount())

  //  private def manipulateSession(nino: String)(implicit hc: HeaderCarrier,
  //                                              request: Request[_]): Future[Result] = {
  //    val eligibleCheckResponse = EligibilityCheckResponse("manual account creation", 1, "manual account creation", 0)
  //    val result = for {
  //      sessionUserInfo ← getPersonalDetails(EligibilityCheckResult(eligibleCheckResponse), nino)
  //      _ ← keyStoreConnector.put(HtsSession(sessionUserInfo))
  //
  //    }
  //
  //
  ////    match {
  ////      case Right(s) ⇒ keyStoreConnector.put(HtsSession(s))
  ////      case Left(error) ⇒ logger.warn(s"an error occurred during personal details retrieval, error: $error")
  ////    }
  //  }

  def createManualAccount: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    setSessionManualCreation(SeeOther(routes.StrideController.getEligibilityPage().url),
                             whenIneligible = { (response, manualCreationAllowed) ⇒
        if (!manualCreationAllowed) {
          Future.successful(SeeOther(routes.StrideController.customerEligible().url))
        } else {
          createAccount(response.nSIUserInfo, 0)
        }
      }
    )
  }(routes.StrideController.createManualAccount())

  def getAccountCreatedPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url),
                 whenEligible = { (_, detailsConfirmed) ⇒
        if (!detailsConfirmed) {
          SeeOther(routes.StrideController.customerEligible().url)
        } else {
          keyStoreConnector.put(HtsSession(UserInfo.AlreadyHasAccount)).fold({
            e ⇒
              logger.warn(s"Could not write to keystore: $e")
              SeeOther(routes.StrideController.getErrorPage().url)
          }, { _ ⇒
            Ok(views.html.account_created())
          }
          )
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

  private def getPersonalDetails(r:           EligibilityCheckResult,
                                 ninoEncoded: String)(implicit hc: HeaderCarrier,
                                                      request: Request[_]): EitherT[Future, String, UserInfo] =
    r match {
      case Eligible(value) ⇒
        helpToSaveConnector.getNSIUserInfo(ninoEncoded).map(UserInfo.EligibleWithNSIUserInfo(value, _))

      case EligibilityCheckResult.Ineligible(value) ⇒
        EitherT.pure[Future, String](UserInfo.Ineligible(value))

      case EligibilityCheckResult.AlreadyHasAccount(value) ⇒
        EitherT.pure[Future, String](UserInfo.AlreadyHasAccount)

    }

}
