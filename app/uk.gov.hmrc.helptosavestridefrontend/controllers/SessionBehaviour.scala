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
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.SessionEligiblityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavestridefrontend.models.NSIUserInfo
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
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
                   whenEligible:          (Eligible, Boolean, NSIUserInfo) ⇒ Future[Result] = (_, _, _) ⇒ SeeOther(routes.StrideController.customerEligible().url),
                   whenIneligible:        (Ineligible, NSIUserInfo) ⇒ Future[Result]        = (_, _) ⇒ SeeOther(routes.StrideController.customerNotEligible().url),
                   whenAlreadyHasAccount: NSIUserInfo ⇒ Future[Result] = _ ⇒ SeeOther(routes.StrideController.accountAlreadyExists().url)
  )(implicit request: Request[_]): Future[Result] =
    checkSessionInternal(
      noSessionData,

      htsSession ⇒
        htsSession.userInfo match {
          case e: Eligible       ⇒ whenEligible(e, htsSession.detailsConfirmed, htsSession.nSIUserInfo)
          case i: Ineligible     ⇒ whenIneligible(i, htsSession.nSIUserInfo)
          case AlreadyHasAccount ⇒ whenAlreadyHasAccount(htsSession.nSIUserInfo)
        }
    )

}

object SessionBehaviour {

  sealed trait SessionEligiblityCheckResult

  object SessionEligiblityCheckResult {

    case class Eligible(response: EligibilityCheckResponse) extends SessionEligiblityCheckResult

    case class Ineligible(response: EligibilityCheckResponse, manualCreationAllowed: Boolean) extends SessionEligiblityCheckResult

    case object AlreadyHasAccount extends SessionEligiblityCheckResult

    def fromEligibilityCheckResult(result: EligibilityCheckResult): SessionEligiblityCheckResult = result match {
      case EligibilityCheckResult.Eligible(value)      ⇒ Eligible(value)
      case EligibilityCheckResult.Ineligible(value)    ⇒ Ineligible(value, manualCreationAllowed = false)
      case EligibilityCheckResult.AlreadyHasAccount(_) ⇒ AlreadyHasAccount
    }

  }

  implicit val format: Format[SessionEligiblityCheckResult] = new Format[SessionEligiblityCheckResult] {
    override def writes(u: SessionEligiblityCheckResult): JsValue = {
      val (code, result, manualCreationAllowed) = u match {
        case Eligible(value)                   ⇒ (1, Some(value), None)
        case Ineligible(value, manualCreation) ⇒ (2, Some(value), Some(manualCreation))
        case AlreadyHasAccount                 ⇒ (3, None, None)
      }

      val fields: List[(String, JsValue)] =
        List("code" → Some(JsNumber(code)),
          "result" → result.map(Json.toJson(_)),
          "manualCreationAllowed" → manualCreationAllowed.map(Json.toJson(_))
        ).collect { case (key, Some(value)) ⇒ key → value }

      JsObject(fields)
    }

    override def reads(json: JsValue): JsResult[SessionEligiblityCheckResult] = {
      ((json \ "code").validate[Int],
        (json \ "result").validateOpt[EligibilityCheckResponse],
        (json \ "manualCreationAllowed").validate[Boolean]) match {
          case (JsSuccess(1, _), JsSuccess(Some(value), _), _) ⇒ JsSuccess(Eligible(value))
          case (JsSuccess(2, _), JsSuccess(Some(value), _), JsSuccess(manualCreationAllowed, _)) ⇒ JsSuccess(Ineligible(value, manualCreationAllowed))
          case (JsSuccess(3, _), JsSuccess(Some(value), _), _) ⇒ JsSuccess(AlreadyHasAccount)
          case _ ⇒ JsError(s"error during parsing eligibility from json $json")
        }
    }
  }

  case class HtsSession(userInfo: SessionEligiblityCheckResult, nSIUserInfo: NSIUserInfo, detailsConfirmed: Boolean = false)

  object HtsSession {
    implicit val format: Format[HtsSession] = Json.format[HtsSession]
  }

}

