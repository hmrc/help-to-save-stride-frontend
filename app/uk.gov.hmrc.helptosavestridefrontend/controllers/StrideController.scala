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
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.{HtsSession, UserInfo}
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.UserInfo.{AlreadyHasAccount, EligibleWithNSIUserInfo, Ineligible}
import uk.gov.hmrc.helptosavestridefrontend.forms.GiveNINOForm
import uk.gov.hmrc.helptosavestridefrontend.models.CreateAccountResult.{AccountAlreadyExists, AccountCreated}
import uk.gov.hmrc.helptosavestridefrontend.models.EnrolmentStatus
import uk.gov.hmrc.helptosavestridefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult.Eligible
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
        internalServerError()
      },
      _ ⇒ Ok(views.html.get_eligibility_page(GiveNINOForm.giveNinoForm))
    )
  }(routes.StrideController.getEligibilityPage())

  def updateSessionIfEnrolled(enrolmentStatus: EnrolmentStatus)(implicit hc: HeaderCarrier): EitherT[Future, String, Unit] = enrolmentStatus match {
    case Enrolled    ⇒ keyStoreConnector.put(HtsSession(AlreadyHasAccount)).map(_ ⇒ ())
    case NotEnrolled ⇒ EitherT.pure[Future, String, Unit](())
  }

  private def checkIfAlreadyEnrolled(nino: String)(ifNotEnrolled: ⇒ Future[Result])(implicit request: Request[AnyContent], hc: HeaderCarrier): Future[Result] = {
    val result = for {
      status ← helpToSaveConnector.getEnrolmentStatus(nino)
      _ ← updateSessionIfEnrolled(status)
    } yield status

    result.fold(
      {
        e ⇒
          logger.warn(e)
          toFuture(internalServerError())
      }, {
        case Enrolled    ⇒ toFuture(Ok(views.html.account_already_exists()))
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
                internalServerError()
              }, {
                case UserInfo.EligibleWithNSIUserInfo(_, details) ⇒
                  Ok(views.html.you_are_eligible(details))
                case UserInfo.Ineligible(_) ⇒
                  SeeOther(routes.StrideController.customerNotEligible().url)
                case UserInfo.AlreadyHasAccount ⇒
                  SeeOther(routes.StrideController.accountAlreadyExists().url)
              }
            )
          })
    )
  }(routes.StrideController.checkEligibilityAndGetPersonalInfo())

  def customerNotEligible: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url), {
      _.userInfo match {
        case EligibleWithNSIUserInfo(_, _) ⇒
          SeeOther(routes.StrideController.customerEligible().url)

        case Ineligible(_) ⇒
          Ok(views.html.you_are_not_eligible())

        case AlreadyHasAccount ⇒
          SeeOther(routes.StrideController.accountAlreadyExists().url)
      }
    })
  }(routes.StrideController.customerNotEligible())

  def accountAlreadyExists: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url), {
      _.userInfo match {
        case EligibleWithNSIUserInfo(_, _) ⇒
          SeeOther(routes.StrideController.customerEligible().url)

        case Ineligible(_) ⇒
          SeeOther(routes.StrideController.customerNotEligible().url)

        case AlreadyHasAccount ⇒
          Ok(views.html.account_already_exists())
      }
    })
  }(routes.StrideController.accountAlreadyExists())

  def customerEligible: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url))
  }(routes.StrideController.customerEligible())

  def handleDetailsConfirmed: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(
      SeeOther(routes.StrideController.getEligibilityPage().url),
      session ⇒
        keyStoreConnector.put(session.copy(detailsConfirmed = true)).fold(
          error ⇒ {
            logger.warn(error)
            internalServerError()
          },
          _ ⇒ SeeOther(routes.StrideController.getCreateAccountPage().url)
        )

    )
  }(routes.StrideController.handleDetailsConfirmed())

  def getCreateAccountPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(
      SeeOther(routes.StrideController.getEligibilityPage().url),
      htsSession ⇒
        if (!htsSession.detailsConfirmed) {
          SeeOther(routes.StrideController.customerEligible().url)
        } else {
          checkIsEligible(_ ⇒ Ok(views.html.create_account()))(htsSession)
        }
    )
  }(routes.StrideController.getCreateAccountPage())

  def getAccountCreatedPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url),
      htsSession ⇒
        if (!htsSession.detailsConfirmed) {
          SeeOther(routes.StrideController.customerEligible().url)
        } else {
          Ok(views.html.account_created())
        }
    )
  }(routes.StrideController.getAccountCreatedPage())

  private def checkIsEligible(ifEligible: EligibleWithNSIUserInfo ⇒ Future[Result])(htsSession: HtsSession): Future[Result] =
    htsSession.userInfo match {
      case e: EligibleWithNSIUserInfo ⇒
        ifEligible(e)

      case UserInfo.Ineligible(_) ⇒
        SeeOther(routes.StrideController.customerNotEligible().url)

      case UserInfo.AlreadyHasAccount ⇒
        SeeOther(routes.StrideController.accountAlreadyExists().url)

    }

  private def getPersonalDetails(r:           EligibilityCheckResult,
                                 ninoEncoded: String)(implicit hc: HeaderCarrier,
                                                      request: Request[_]): EitherT[Future, String, UserInfo] =
    r match {
      case Eligible(value) ⇒
        helpToSaveConnector.getNSIUserInfo(ninoEncoded).map(UserInfo.EligibleWithNSIUserInfo(value, _))

      case EligibilityCheckResult.Ineligible(value) ⇒
        EitherT.pure[Future, String, UserInfo](UserInfo.Ineligible(value))

      case EligibilityCheckResult.AlreadyHasAccount(value) ⇒
        EitherT.pure[Future, String, UserInfo](UserInfo.AlreadyHasAccount)

    }

  def createAccount: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url),
                 checkIsEligible(result ⇒ helpToSaveConnector.createAccount(result.nSIUserInfo)
        .fold(
          error ⇒ {
            logger.warn(s"error during create account call, error: $error")
            internalServerError()
          }, {
            case AccountCreated ⇒
              Ok(views.html.account_created())
            case AccountAlreadyExists ⇒
              Ok(views.html.account_already_exists())
          }
        ))
    )
  }(routes.StrideController.createAccount())

}
