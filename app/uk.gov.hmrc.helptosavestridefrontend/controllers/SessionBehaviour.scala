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
import play.api.mvc.{Request, Result}
import uk.gov.hmrc.helptosavestridefrontend.models.{HtsSession, NSIPayload}
import uk.gov.hmrc.helptosavestridefrontend.models.SessionEligibilityCheckResult._
import uk.gov.hmrc.helptosavestridefrontend.repo.SessionStore
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

trait SessionBehaviour {
  this: StrideFrontendController with Logging ⇒

  val sessionStore: SessionStore

  private def checkSessionInternal(noSessionData: ⇒ Future[Result],
                                   whenSession:   HtsSession ⇒ Future[Result])(
      implicit
      hc: HeaderCarrier, request: Request[_]
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

  def checkSession(noSessionData:         ⇒ Future[Result],
                   whenEligible:          (Eligible, Boolean, NSIPayload) ⇒ Future[Result] = (_, _, _) ⇒ SeeOther(routes.StrideController.customerEligible().url),
                   whenIneligible:        (Ineligible, NSIPayload) ⇒ Future[Result]        = (_, _) ⇒ SeeOther(routes.StrideController.customerNotEligible().url),
                   whenAlreadyHasAccount: NSIPayload ⇒ Future[Result] = _ ⇒ SeeOther(routes.StrideController.accountAlreadyExists().url)
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
