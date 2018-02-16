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

import cats.instances.future._
import play.api.libs.json._
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.helptosavestridefrontend.connectors.KeyStoreConnector
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.HtsSession
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.UserInfo.{AlreadyHasAccount, EligibleWithNSIUserInfo, Ineligible}
import uk.gov.hmrc.helptosavestridefrontend.models.{NSIUserInfo, PayePersonalDetails}
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, toFuture}
import uk.gov.hmrc.helptosavestridefrontend.views
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SessionBehaviour {
  this: StrideFrontendController with Logging ⇒

  val keyStoreConnector: KeyStoreConnector

  def checkSession(noSessionData: ⇒ Future[Result],
                   whenSession:   HtsSession ⇒ Future[Result])(implicit hc: HeaderCarrier, request: Request[_]): Future[Result] =
    keyStoreConnector
      .get
      .fold({
        error ⇒
          logger.warn(s"error during retrieving UserSessionInfo from keystore, error= $error")
          toFuture(internalServerError())
      },
        _.fold(noSessionData)(whenSession)
      ).flatMap(identity)

  def checkSession(noSessionData: ⇒ Future[Result])(implicit request: Request[_]): Future[Result] =
    checkSession(
      noSessionData,
      htsSession ⇒ htsSession.userInfo match {
        case EligibleWithNSIUserInfo(_, nSIUserInfo) ⇒
          Ok(views.html.you_are_eligible(nSIUserInfo))

        case Ineligible(response) ⇒
          SeeOther(routes.StrideController.youAreNotEligible().url)

        case AlreadyHasAccount(response) ⇒
          SeeOther(routes.StrideController.accountAlreadyExists().url)
      })
}

object SessionBehaviour {

  sealed trait UserInfo

  object UserInfo {

    case class EligibleWithNSIUserInfo(response: EligibilityCheckResponse, nSIUserInfo: NSIUserInfo) extends UserInfo

    case class Ineligible(response: EligibilityCheckResponse) extends UserInfo

    case class AlreadyHasAccount(response: EligibilityCheckResponse) extends UserInfo

  }

  implicit val format: Format[UserInfo] = new Format[UserInfo] {
    override def writes(result: UserInfo): JsValue = {
      val (a, b, c) = result match {
        case EligibleWithNSIUserInfo(value, details) ⇒ (1, value, Some(details))
        case Ineligible(value)                       ⇒ (2, value, None)
        case AlreadyHasAccount(value)                ⇒ (3, value, None)
      }

      val fields = {
        val f = List("code" -> JsNumber(a), "result" -> Json.toJson(b))
        c.fold(f)(d ⇒ ("details" → Json.toJson(d)) :: f)
      }
      JsObject(fields)
    }

    override def reads(json: JsValue): JsResult[UserInfo] = {
      ((json \ "code").validate[Int],
        (json \ "result").validate[EligibilityCheckResponse],
        (json \ "details").validateOpt[NSIUserInfo]) match {
          case (JsSuccess(1, _), JsSuccess(value, _), JsSuccess(Some(details), _)) ⇒ JsSuccess(EligibleWithNSIUserInfo(value, details))
          case (JsSuccess(2, _), JsSuccess(value, _), _) ⇒ JsSuccess(Ineligible(value))
          case (JsSuccess(3, _), JsSuccess(value, _), _) ⇒ JsSuccess(AlreadyHasAccount(value))
          case _ ⇒ JsError(s"error during parsing eligibility from json $json")
        }
    }
  }

  case class HtsSession(userInfo: UserInfo, detailsConfirmed: Boolean = false)

  object HtsSession {
    implicit val format: Format[HtsSession] = Json.format[HtsSession]
  }

}

