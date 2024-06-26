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

import com.google.inject.Singleton
import javax.inject.Inject
import play.api.mvc._
import uk.gov.hmrc.helptosavestridefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.frontend.controller.FrontendController

@Singleton
class StrideFrontendController @Inject() (
  appConfig: FrontendAppConfig,
  mcc: MessagesControllerComponents,
  errorHandler: ErrorHandler
) extends FrontendController(mcc) {

  override implicit def hc(implicit rh: RequestHeader): HeaderCarrier =
    HeaderCarrierConverter.fromRequestAndSession(rh, rh.session)

  def internalServerError()(implicit request: Request[_]): Result =
    InternalServerError(errorHandler.internalServerErrorTemplate(request))

}
