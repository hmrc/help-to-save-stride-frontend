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

package uk.gov.hmrc.helptosavestridefrontend.auth

import java.util.Base64

import cats.instances.list._
import cats.instances.option._
import cats.syntax.traverse._
import configs.syntax._
import play.api.mvc._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.models.OperatorDetails
import uk.gov.hmrc.helptosavestridefrontend.util.toFuture
import uk.gov.hmrc.play.bootstrap.config.AuthRedirects
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.Future

trait StrideAuth extends AuthorisedFunctions with AuthRedirects {
  this: FrontendController ⇒

  val frontendAppConfig: FrontendAppConfig

  val config: Configuration = frontendAppConfig.runModeConfiguration

  val env: Environment = frontendAppConfig.environment

  private val requiredRoles: List[String] = {
    val base64Values = config.underlying.get[List[String]]("stride.base64-encoded-roles").value
    base64Values.map(x ⇒ new String(Base64.getDecoder.decode(x)))
  }

  private val getRedirectUrl: (Request[AnyContent], Call) ⇒ String = if (config.underlying.get[Boolean]("stride.redirect-with-absolute-urls").value) {
    case (r, c) ⇒ c.absoluteURL()(r)
  } else {
    case (_, c) ⇒ c.url
  }

  def authorisedFromStride(action: Request[AnyContent] ⇒ Future[Result])(redirectCall: Call): Action[AnyContent] =
    Action.async { implicit request ⇒
      authorised(AuthProviders(PrivilegedApplication)).retrieve(allEnrolments){
        enrolments ⇒
          necessaryRoles(enrolments).fold[Future[Result]](Unauthorized("Insufficient roles")){ _ ⇒ action(request) }
      }.recover{
        case _: NoActiveSession ⇒
          toStrideLogin(getRedirectUrl(request, redirectCall))
      }
    }

  def authorisedFromStrideWithDetails(action: Request[AnyContent] ⇒ OperatorDetails ⇒ Future[Result])(redirectCall: Call): Action[AnyContent] =
    Action.async { implicit request ⇒
      authorised(AuthProviders(PrivilegedApplication)).retrieve(allEnrolments and credentials and name and email) {
        case enrolments ~ creds ~ name ~ email ⇒
          necessaryRoles(enrolments).fold[Future[Result]](Unauthorized("Insufficient roles")) {
            roles ⇒ action(request)(OperatorDetails(roles.map(_.key), creds.map(_.providerId), getName(name), email.getOrElse("")))
          }
      }.recover {
        case _: NoActiveSession ⇒
          toStrideLogin(getRedirectUrl(request, redirectCall))
      }
    }

  private def necessaryRoles(enrolments: Enrolments) =
    requiredRoles.map(enrolments.getEnrolment).traverse[Option, Enrolment](identity)

  private def getName(name: Option[Name]): String =
    (name.flatMap(_.name).toList ++ name.flatMap(_.lastName).toList).mkString(" ")
}
