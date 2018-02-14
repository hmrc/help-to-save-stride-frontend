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
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.UserSessionInfo
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.UserSessionInfo.EligibleWithPayePersonalDetails
import uk.gov.hmrc.helptosavestridefrontend.forms.GiveNINOForm
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
            _ ← keyStoreConnector.put(sessionUserInfo)
          } yield sessionUserInfo

          r.fold(
            error ⇒ {
              logger.warn(s"error during retrieving eligibility result and paye-personal-info, error: $error")
              internalServerError()
            }, {
              case UserSessionInfo.EligibleWithPayePersonalDetails(_, details) ⇒
                Ok(views.html.you_are_eligible(details))
              case UserSessionInfo.Ineligible(_) ⇒
                SeeOther(routes.StrideController.youAreNotEligible().url)
              case UserSessionInfo.AlreadyHasAccount(_) ⇒
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

  def getTermsAndConditionsPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    checkSession(SeeOther(routes.StrideController.getEligibilityPage().url),
                 checkIsEligible(_ ⇒ Ok(views.html.terms_and_conditions()))
    )
  }(routes.StrideController.getTermsAndConditionsPage().url)

  private def checkIsEligible(ifEligible: EligibleWithPayePersonalDetails ⇒ Future[Result])(userInfo: UserSessionInfo): Future[Result] =
    userInfo match {
      case e: EligibleWithPayePersonalDetails ⇒
        ifEligible(e)

      case UserSessionInfo.Ineligible(_) ⇒
        SeeOther(routes.StrideController.youAreNotEligible().url)

      case UserSessionInfo.AlreadyHasAccount(_) ⇒
        SeeOther(routes.StrideController.accountAlreadyExists().url)

    }

  private def getPersonalDetails(r:           EligibilityCheckResult,
                                 ninoEncoded: String)(implicit hc: HeaderCarrier,
                                                      request: Request[_]): EitherT[Future, String, UserSessionInfo] =
    r match {
      case Eligible(value) ⇒
        helpToSaveConnector.getPayePersonalDetails(ninoEncoded).map(UserSessionInfo.EligibleWithPayePersonalDetails(value, _))

      case Ineligible(value) ⇒
        EitherT.pure[Future, String, UserSessionInfo](UserSessionInfo.Ineligible(value))

      case AlreadyHasAccount(value) ⇒
        EitherT.pure[Future, String, UserSessionInfo](UserSessionInfo.AlreadyHasAccount(value))

    }
}
