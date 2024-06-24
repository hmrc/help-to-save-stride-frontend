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

package uk.gov.hmrc.helptosavestridefrontend.auth

import play.api.{Configuration, Environment}
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.controllers.StrideFrontendController
import uk.gov.hmrc.helptosavestridefrontend.models.RoleType._
import uk.gov.hmrc.helptosavestridefrontend.models.{OperatorDetails, RoleType}
import uk.gov.hmrc.helptosavestridefrontend.util.toFuture
import java.util.Base64
import scala.concurrent.{ExecutionContext, Future}

trait StrideAuth extends AuthorisedFunctions with StrideAuthRedirects {
  this: StrideFrontendController =>

  val frontendAppConfig: FrontendAppConfig

  val config: Configuration = frontendAppConfig.config

  val env: Environment = frontendAppConfig.environment

  private val requiredRoles = {
    val base64Values = config.get[Seq[String]]("stride.base64-encoded-roles")
    base64Values.map(x => new String(Base64.getDecoder.decode(x)))
  }

  private val secureRoles = {
    val base64SecureValues = config.get[Seq[String]]("stride.base64-encoded-secure-roles")
    base64SecureValues.map(x => new String(Base64.getDecoder.decode(x)))
  }

  private val getRedirectUrl: (Request[AnyContent], Call) => String =
    if (config.get[Boolean]("stride.redirect-with-absolute-urls")) { case (r, c) =>
      c.absoluteURL()(r)
    }
    else { case (_, c) =>
      c.url
    }

  def authorisedFromStride(
    action: Request[AnyContent] => RoleType => Future[Result]
  )(redirectCall: Call)(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthProviders(PrivilegedApplication))
        .retrieve(allEnrolments) { enrolments =>
          necessaryRoles(enrolments).fold[Future[Result]](Unauthorized("Insufficient roles")) { roles =>
            action(request)(roles)
          }
        }
        .recover { case _: NoActiveSession =>
          toStrideLogin(getRedirectUrl(request, redirectCall))
        }
    }

  def authorisedFromStrideWithDetails(
    action: Request[AnyContent] => OperatorDetails => RoleType => Future[Result]
  )(redirectCall: Call)(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthProviders(PrivilegedApplication))
        .retrieve(allEnrolments and credentials and name and email) { case enrolments ~ creds ~ name ~ email =>
          necessaryRoles(enrolments).fold[Future[Result]](Unauthorized("Insufficient roles")) { roles =>
            action(request)(
              OperatorDetails(roles.roleNames, creds.map(_.providerId), getName(name), email.getOrElse(""))
            )(
              roles
            )
          }
        }
        .recover { case _: NoActiveSession =>
          toStrideLogin(getRedirectUrl(request, redirectCall))
        }
    }

  private def necessaryRoles(enrolments: Enrolments) = {
    val enrolmentKeys = enrolments.enrolments.map(_.key).toList
    if (enrolmentKeys.exists(secureRoles.contains(_))) {
      Some(Secure(enrolmentKeys))
    } else if (enrolmentKeys.exists(requiredRoles.contains(_))) {
      Some(Standard(enrolmentKeys))
    } else {
      None
    }
  }

  private def getName(name: Option[Name]) =
    (name.flatMap(_.name).toList ++ name.flatMap(_.lastName).toList).mkString(" ")
}
