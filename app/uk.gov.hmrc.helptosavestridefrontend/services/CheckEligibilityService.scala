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

package uk.gov.hmrc.helptosavestridefrontend.services

import cats.data.EitherT
import cats.syntax.either._
import cats.instances.future._
import com.google.inject.Inject
import play.api.i18n.MessagesApi
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.connectors.HelpToSaveConnector
import uk.gov.hmrc.helptosavestridefrontend.controllers.StrideFrontendController
import uk.gov.hmrc.helptosavestridefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult
import uk.gov.hmrc.helptosavestridefrontend.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class CheckEligibilityService @Inject() (val authConnector:       AuthConnector,
                                         val helpToSaveConnector: HelpToSaveConnector,
                                         val frontendAppConfig:   FrontendAppConfig,
                                         messageApi:              MessagesApi) extends Logging {

  val strideFrontendController = new StrideFrontendController(messageApi, frontendAppConfig)

  def doEligibilityCheck(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): EitherT[Future, String, EligibilityCheckResult] = {
    helpToSaveConnector.getEnrolmentStatus(nino).fold(
      error ⇒ {
        logger.warn(s"error during create account call, error: $error")
        Left("Error getting enrolment status from Enrolment store")
      }, {
        case Enrolled ⇒ Right(Enrolled)
        case NotEnrolled ⇒
          Right(helpToSaveConnector.getEligibility(nino))
      }
    )
  }

}
