/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavestridefrontend

import java.util.Base64

import configs.syntax._
import org.scalamock.handlers.CallHandler4
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait AuthSupport { this: TestSupport ⇒

  lazy val roles: List[String] = {
    val base64EncodedRoles = fakeApplication.configuration.underlying.get[List[String]]("stride.base64-encoded-roles").value
    base64EncodedRoles.map(x ⇒ new String(Base64.getDecoder.decode(x)))
  }

  lazy val secureRoles: List[String] = {
    val base64SecureValues = fakeApplication.configuration.underlying.get[List[String]]("stride.base64-encoded-secure-roles").value
    base64SecureValues.map(x ⇒ new String(Base64.getDecoder.decode(x)))
  }

  type RetrievalsType = Enrolments ~ Option[Credentials] ~ Option[Name] ~ Option[String]
  val retrievals = new ~(new ~(new ~(Enrolments(roles.map(Enrolment(_)).toSet), Some(Credentials("PID", "pidType"))), Some(Name(Some("name"), None))), Some("email"))
  val secureRetrievals = new ~(new ~(new ~(Enrolments(secureRoles.map(Enrolment(_)).toSet), Some(Credentials("PID", "pidType"))), Some(Name(Some("name"), None))), Some("email"))

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuthorised[A](expectedPredicate: Predicate,
                        expectedRetrieval: Retrieval[A])(result: Either[Throwable, A]): CallHandler4[Predicate, Retrieval[A], HeaderCarrier, ExecutionContext, Future[A]] =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[A])(_: HeaderCarrier, _: ExecutionContext))
      .expects(expectedPredicate, expectedRetrieval, *, *)
      .returning(result.fold(Future.failed, Future.successful))

  def mockSuccessfulAuthorisation(): CallHandler4[Predicate, Retrieval[Enrolments], HeaderCarrier, ExecutionContext, Future[Enrolments]] =
    mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(
      Right(Enrolments(roles.map(Enrolment(_)).toSet)))

  def mockSuccessfulSecureAuthorisation(): CallHandler4[Predicate, Retrieval[Enrolments], HeaderCarrier, ExecutionContext, Future[Enrolments]] =
    mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(
      Right(Enrolments(secureRoles.map(Enrolment(_)).toSet)))

  def mockSuccessfulAuthorisationWithDetails(): CallHandler4[Predicate, Retrieval[RetrievalsType], HeaderCarrier, ExecutionContext, Future[RetrievalsType]] =
    mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments and credentials and name and email)(
      Right(retrievals))

  def mockSuccessfulSecureAuthorisationWithDetails(): CallHandler4[Predicate, Retrieval[RetrievalsType], HeaderCarrier, ExecutionContext, Future[RetrievalsType]] =
    mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments and credentials and name and email)(
      Right(secureRetrievals))

  def mockAuthFail(): Unit =
    mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(
      Left(BearerTokenExpired("no login session exists")))
}
