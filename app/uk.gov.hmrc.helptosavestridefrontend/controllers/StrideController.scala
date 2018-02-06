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
import play.api.mvc.{Action, AnyContent, Request}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavestridefrontend.auth.StrideAuth
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosavestridefrontend.forms.{GiveNINOForm, NINOValidation}
import uk.gov.hmrc.helptosavestridefrontend.models.PayePersonalDetails
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, NINOLogMessageTransformer, base64Encode, toFuture}
import uk.gov.hmrc.helptosavestridefrontend.views
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class StrideController @Inject() (val authConnector:       AuthConnector,
                                  val helpToSaveConnector: HelpToSaveConnector,
                                  val frontendAppConfig:   FrontendAppConfig,
                                  messageApi:              MessagesApi)(implicit val ninoValidation: NINOValidation,
                                                                        transformer: NINOLogMessageTransformer)
  extends StrideFrontendController(messageApi, frontendAppConfig) with StrideAuth with I18nSupport with Logging {

  def getEligibilityPage: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    Ok(views.html.get_eligibility_page(GiveNINOForm.giveNinoForm))
  }(routes.StrideController.getEligibilityPage())

  def checkEligibilityAndGetPersonalInfo: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    GiveNINOForm.giveNinoForm.bindFromRequest().fold(
      withErrors ⇒ Ok(views.html.get_eligibility_page(withErrors)),
      form ⇒ {
        val ninoEncoded = new String(base64Encode(form.nino))

        val r = for {
          eligibility ← helpToSaveConnector.getEligibility(ninoEncoded)
          personalDetails ← getPersonalDetails(eligibility, ninoEncoded)
        } yield eligibility -> personalDetails

        r.fold(
          error ⇒ {
            logger.warn(s"error during get eligibility result and pay-personal-info, error: $error")
            internalServerError()
          },
          {
            case (Eligible(_), Some(details)) ⇒
              Ok(views.html.you_are_eligible(details))
            case (Eligible(_), None) ⇒
              logger.warn("user is eligible but could not retrieve pay-personal-info")
              SeeOther(routes.StrideController.noPayeDetailsFound().url)
            case (Ineligible(_), _) ⇒
              SeeOther(routes.StrideController.youAreNotEligible().url)
            case (AlreadyHasAccount(_), _) ⇒
              SeeOther(routes.StrideController.accountAlreadyExists().url)
            case _ ⇒
              logger.warn("unknown error during checking eligibility and pay-personal-details")
              internalServerError()
          }
        )

      })
  }(routes.StrideController.checkEligibilityAndGetPersonalInfo())

  def youAreNotEligible: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    Ok(views.html.you_are_not_eligible())
  }(routes.StrideController.youAreNotEligible())

  def youAreEligible: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    Ok("you are eligible") // TODO: Implement this once user info is stored in keystore
  }(routes.StrideController.youAreEligible())

  def accountAlreadyExists: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    Ok(views.html.account_already_exists())
  }(routes.StrideController.accountAlreadyExists())

  def noPayeDetailsFound: Action[AnyContent] = authorisedFromStride { implicit request ⇒
    Ok(views.html.no_paye_details_found())
  }(routes.StrideController.noPayeDetailsFound())

  private def getPersonalDetails(r: EligibilityCheckResult, nino: String)(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, String, Option[PayePersonalDetails]] =
    r match {

      case Eligible(_) ⇒
        helpToSaveConnector.getPayePersonalDetails(nino).map(_.payeDetails)

      case Ineligible(_) ⇒
        EitherT.pure[Future, String, Option[PayePersonalDetails]](None)

      case AlreadyHasAccount(_) ⇒
        EitherT.pure[Future, String, Option[PayePersonalDetails]](None)

    }
}
