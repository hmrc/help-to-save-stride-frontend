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
import uk.gov.hmrc.helptosavestridefrontend.models.NSIUserInfo
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait SessionBehaviour {
  this: StrideFrontendController with Logging ⇒

  val keyStoreConnector: KeyStoreConnector

  private def checkSessionInternal(noSessionData: ⇒ Future[Result],
                                   whenSession:   HtsSession ⇒ Future[Result])(
      implicit
      hc: HeaderCarrier, request: Request[_]
  ): Future[Result] =
    keyStoreConnector
      .get
      .fold[Future[Result]]({
        error ⇒
          logger.warn(s"error during retrieving UserSessionInfo from keystore, error= $error")
          SeeOther(routes.StrideController.getErrorPage().url)
      },
        _.fold(noSessionData)(whenSession)
      ).flatMap(identity)

  def checkSession(noSessionData:         ⇒ Future[Result],
                   whenEligible:          (EligibleWithNSIUserInfo, Boolean) ⇒ Future[Result] = (_, _) ⇒ SeeOther(routes.StrideController.customerEligible().url),
                   whenIneligible:        Ineligible ⇒ Future[Result] = _ ⇒ SeeOther(routes.StrideController.customerNotEligible().url),
                   whenAlreadyHasAccount: () ⇒ Future[Result]                                 = () ⇒ SeeOther(routes.StrideController.accountAlreadyExists().url)
  )(implicit request: Request[_]): Future[Result] =
    checkSessionInternal(
      noSessionData,

      htsSession ⇒
        htsSession.userInfo match {
          case e: EligibleWithNSIUserInfo ⇒ whenEligible(e, htsSession.detailsConfirmed)
          case i: Ineligible              ⇒ whenIneligible(i)
          case AlreadyHasAccount          ⇒ whenAlreadyHasAccount()
        }
    )

}

object SessionBehaviour {

  sealed trait UserInfo

  object UserInfo {

    case class EligibleWithNSIUserInfo(response: EligibilityCheckResponse, nSIUserInfo: NSIUserInfo) extends UserInfo

    case class Ineligible(response: EligibilityCheckResponse, nSIUserInfo: Option[NSIUserInfo]) extends UserInfo

    case object AlreadyHasAccount extends UserInfo

  }

  implicit val format: Format[UserInfo] = new Format[UserInfo] {
    override def writes(u: UserInfo): JsValue = {
      val (code, result, details) = u match {
        case EligibleWithNSIUserInfo(value, details) ⇒ (1, Some(value), Some(details))
        case Ineligible(value, Some(details))        ⇒ (2, Some(value), Some(details))
        case Ineligible(value, None)                 ⇒ (2, Some(value), None)
        case AlreadyHasAccount                       ⇒ (3, None, None)
      }

      val fields: List[(String, JsValue)] =
        List("code" → Some(JsNumber(code)),
          "result" → result.map(Json.toJson(_)),
          "details" → details.map(Json.toJson(_))
        ).collect { case (key, Some(value)) ⇒ key → value }

      JsObject(fields)
    }

    override def reads(json: JsValue): JsResult[UserInfo] = {
      ((json \ "code").validate[Int],
        (json \ "result").validateOpt[EligibilityCheckResponse],
        (json \ "details").validateOpt[NSIUserInfo]) match {
          case (JsSuccess(1, _), JsSuccess(Some(value), _), JsSuccess(Some(details), _)) ⇒ JsSuccess(EligibleWithNSIUserInfo(value, details))
          case (JsSuccess(2, _), JsSuccess(Some(value), _), JsSuccess(Some(details), _)) ⇒ JsSuccess(Ineligible(value, Some(details)))
          case (JsSuccess(2, _), JsSuccess(Some(value), _), JsSuccess(None, _)) ⇒ JsSuccess(Ineligible(value, None))
          case (JsSuccess(3, _), JsSuccess(None, _), _) ⇒ JsSuccess(AlreadyHasAccount)
          case _ ⇒ JsError(s"error during parsing eligibility from json $json")
        }
    }
  }

  case class HtsSession(userInfo: UserInfo, nino: String, detailsConfirmed: Boolean = false)

  object HtsSession {
    implicit val format: Format[HtsSession] = Json.format[HtsSession]
  }

}

