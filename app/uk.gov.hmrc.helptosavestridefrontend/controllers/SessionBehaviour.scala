/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.helptosavestridefrontend.auth.StrideAuth
import uk.gov.hmrc.helptosavestridefrontend.models.RoleType._
import uk.gov.hmrc.helptosavestridefrontend.models.SessionEligibilityCheckResult._
import uk.gov.hmrc.helptosavestridefrontend.models._
import uk.gov.hmrc.helptosavestridefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait SessionBehaviour extends StrideAuth {
  this: StrideFrontendController with Logging ⇒

  val sessionStore: SessionStore

  type AccountReferenceNumber = String

  type NINO = String

  private def checkSessionInternal(noSessionData: ⇒ Future[Result],
                                   whenSession:   HtsSession ⇒ Future[Result])(
      implicit
      hc: HeaderCarrier, ec: ExecutionContext
  ): Future[Result] =
    sessionStore
      .get
      .fold[Future[Result]]({
        error ⇒
          logger.warn(s"error during retrieving UserSessionInfo from mongo session, error= $error")
          SeeOther(routes.StrideController.getErrorPage().url)
      },
        _.fold(noSessionData)(whenSession(_))
      ).flatMap(identity)

  def checkSession(roleType: RoleType)(noSessionData:         ⇒ Future[Result],
                                       whenEligible:          (Eligible, Boolean, NSIPayload, Option[AccountReferenceNumber]) ⇒ Future[Result]        = (_, _, _, _) ⇒ SeeOther(routes.StrideController.customerEligible().url),
                                       whenEligibleSecure:    (Eligible, NINO, Option[NSIPayload], Option[AccountReferenceNumber]) ⇒ Future[Result]   = (_, _, _, _) ⇒ SeeOther(routes.StrideController.customerEligible().url),
                                       whenIneligible:        (Ineligible, NSIPayload, Option[AccountReferenceNumber]) ⇒ Future[Result]               = (_, _, _) ⇒ SeeOther(routes.StrideController.customerNotEligible().url),
                                       whenIneligibleSecure:  (Ineligible, NINO, Option[NSIPayload], Option[AccountReferenceNumber]) ⇒ Future[Result] = (_, _, _, _) ⇒ SeeOther(routes.StrideController.customerNotEligible().url),
                                       whenAlreadyHasAccount: (Option[NSIPayload], Option[AccountReferenceNumber]) ⇒ Future[Result]                   = (_, _) ⇒ SeeOther(routes.StrideController.accountAlreadyExists().url)
  )(implicit request: Request[_], ec: ExecutionContext): Future[Result] =
    checkSessionInternal(
      noSessionData, htsSession ⇒

      (htsSession, roleType) match {

        case (session: HtsStandardSession, _: Standard) ⇒
          session.userInfo match {
            case e: Eligible       ⇒ whenEligible(e, session.detailsConfirmed, session.nSIUserInfo, session.accountNumber)
            case i: Ineligible     ⇒ whenIneligible(i, session.nSIUserInfo, session.accountNumber)
            case AlreadyHasAccount ⇒ whenAlreadyHasAccount(Some(session.nSIUserInfo), session.accountNumber)
          }

        case (session: HtsSecureSession, _: Secure) ⇒
          session.userInfo match {
            case e: Eligible       ⇒ whenEligibleSecure(e, session.nino, session.nSIUserInfo, session.accountNumber)
            case AlreadyHasAccount ⇒ whenAlreadyHasAccount(session.nSIUserInfo, session.accountNumber)
            case i: Ineligible     ⇒ whenIneligibleSecure(i, session.nino, session.nSIUserInfo, session.accountNumber)
          }

        case (_, _) ⇒
          Forbidden
      }
    )

}
