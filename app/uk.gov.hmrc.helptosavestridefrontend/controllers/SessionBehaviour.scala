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

import java.util.UUID

import cats.instances.future._
import play.api.libs.json.{Format, Json}
import play.api.mvc.{Request, Result, Session}
import uk.gov.hmrc.helptosavestridefrontend.connectors.KeyStoreConnector
import uk.gov.hmrc.helptosavestridefrontend.models.PayePersonalDetails
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, toFuture}
import uk.gov.hmrc.helptosavestridefrontend.views

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait SessionBehaviour {
  this: StrideFrontendController with Logging ⇒

  val keyStoreConnector: KeyStoreConnector

  val sessionKey: String = "stride-user-info"

  def checkSession(noSession: ⇒ Future[Result], whenSession: UserInfo ⇒ Future[Result])(implicit request: Request[_]): Future[Result] = {

    keyStoreConnector.get(getSessionWithKey.key)
      .fold({
        error ⇒
          logger.warn(s"error during retrieving session from keystore, error= $error")
          toFuture(internalServerError())
      },
        _.fold(noSession)(whenSession)
      ).flatMap(identity)
  }

  private def whenSession(strideSession: UserInfo)(implicit request: Request[_]): Future[Result] = {
    val ks = getSessionWithKey
    val r = (strideSession.eligibilityCheckResult, strideSession.payePersonalDetails) match {
      case (Some(Eligible(_)), Some(details)) ⇒
        Ok(views.html.you_are_eligible(details)).withSession(ks.session)
      case (Some(Ineligible(_)), _) ⇒
        SeeOther(routes.StrideController.youAreNotEligible().url).withSession(ks.session)
      case (Some(AlreadyHasAccount(_)), _) ⇒
        SeeOther(routes.StrideController.accountAlreadyExists().url).withSession(ks.session)
      case _ ⇒
        logger.warn("unknown error during checking eligibility and pay-personal-details")
        internalServerError()
    }
    toFuture(r)
  }

  def checkSession(noSession: ⇒ Future[Result])(implicit request: Request[_]): Future[Result] = {
    checkSession(noSession, whenSession)
  }

  def getSessionWithKey(implicit session: Session): SessionWithKey = {
    session.get(sessionKey) match {
      case Some(id) ⇒ SessionWithKey(id, session)
      case _ ⇒
        val newId = UUID.randomUUID().toString
        val newSession = session.+(sessionKey -> newId)
        SessionWithKey(newId, newSession)
    }
  }

  def newSession(implicit session: Session): Session = session.-(sessionKey)
}

case class UserInfo(eligibilityCheckResult: Option[EligibilityCheckResult],
                    payePersonalDetails:    Option[PayePersonalDetails])

object UserInfo {
  implicit val format: Format[UserInfo] = Json.format[UserInfo]
}

case class SessionWithKey(key: String, session: Session)
