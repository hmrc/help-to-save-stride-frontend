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

import com.google.inject.{Inject, Singleton}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.play.bootstrap.controller.ActionWithMdc

@Singleton
class ForbiddenController @Inject() (implicit messagesApi: MessagesApi, appConfig: FrontendAppConfig)
  extends StrideFrontendController(messagesApi, appConfig) {

  def forbidden: Action[AnyContent] = ActionWithMdc {
    Forbidden("Please ask the HtS Dev team for permissions to access this site")
  }

}
