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
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.UserInfo.EligibleWithNSIUserInfo
import uk.gov.hmrc.helptosavestridefrontend.forms.GiveNINOForm
import uk.gov.hmrc.helptosavestridefrontend.models.CreateAccountResult.{AccountAlreadyExists, AccountCreated}
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, NINOLogMessageTransformer, base64Encode, toFuture}
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
  }(routes.StrideController.getEligibilityPage().url)

  def checkEligibilityAndGetPersonalInfo: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(
      GiveNINOForm.giveNinoForm.bindFromRequest().fold(
        withErrors ⇒ Ok(views.html.get_eligibility_page(withErrors)),
        form ⇒ {
          val ninoEncoded = new String(base64Encode(form.nino))
          val r = for {
            eligibility ← helpToSaveConnector.getEligibility(ninoEncoded)
            sessionUserInfo ← getPersonalDetails(eligibility, ninoEncoded)
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
                SeeOther(routes.StrideController.youAreNotEligible().url)
              case UserInfo.AlreadyHasAccount(_) ⇒
                SeeOther(routes.StrideController.accountAlreadyExists().url)
            }
          )
        })
    )
  }(routes.StrideController.checkEligibilityAndGetPersonalInfo().url)

  def youAreNotEligible: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url))
  }(routes.StrideController.youAreNotEligible().url)

  def youAreEligible: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url))
  }(routes.StrideController.youAreEligible().url)

  def accountAlreadyExists: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url))
  }(routes.StrideController.accountAlreadyExists().url)

  def handleDetailsConfirmed: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(
      SeeOther(routes.StrideController.getEligibilityPage().url),
      session ⇒
        keyStoreConnector.put(session.copy(detailsConfirmed = true)).fold(
          error ⇒ {
            logger.warn(error)
            internalServerError()
          },
          _ ⇒ SeeOther(routes.StrideController.getTermsAndConditionsPage().url)
        )

    )
  }(routes.StrideController.accountAlreadyExists().url)

  def getTermsAndConditionsPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(
      SeeOther(routes.StrideController.getEligibilityPage().url),
      htsSession ⇒
        if (!htsSession.detailsConfirmed) {
          SeeOther(routes.StrideController.youAreEligible().url)
        } else {
          checkIsEligible(_ ⇒ Ok(views.html.terms_and_conditions()))(htsSession)
        }
    )
  }(routes.StrideController.getTermsAndConditionsPage().url)

  def getAccountCreatedPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url),
      htsSession ⇒
        if (!htsSession.detailsConfirmed) {
          SeeOther(routes.StrideController.youAreEligible().url)
        } else {
          Ok(views.html.account_created())
        }
    )
  }(routes.StrideController.getTermsAndConditionsPage().url)

  def getTechnicalErrorPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url),
      checkIsEligible(_ ⇒ Ok(views.html.technical_error())))
  }(routes.StrideController.getTermsAndConditionsPage().url)

  private def checkIsEligible(ifEligible: EligibleWithNSIUserInfo ⇒ Future[Result])(htsSession: HtsSession): Future[Result] =
    htsSession.userInfo match {
      case e: EligibleWithNSIUserInfo ⇒
        ifEligible(e)

      case UserInfo.Ineligible(_) ⇒
        SeeOther(routes.StrideController.youAreNotEligible().url)

      case UserInfo.AlreadyHasAccount(_) ⇒
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
        EitherT.pure[Future, String, UserInfo](UserInfo.AlreadyHasAccount(value))

    }

  def createAccount: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url),
                 checkIsEligible(result ⇒ helpToSaveConnector.createAccount(result.nSIUserInfo)
        .fold(
          error ⇒ {
            logger.warn(s"error during create account call, error: $error")
            InternalServerError(routes.StrideController.getTechnicalErrorPage().url)
          }, {
            case AccountCreated ⇒
              Ok(routes.StrideController.getAccountCreatedPage().url)
            case AccountAlreadyExists ⇒
              Ok(routes.StrideController.accountAlreadyExists().url)
          }
        ))
    )
  }(routes.StrideController.getTermsAndConditionsPage().url)

}
