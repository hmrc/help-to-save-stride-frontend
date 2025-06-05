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

package uk.gov.hmrc.helptosavestridefrontend

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{when => whenMock}
import org.mockito.stubbing.OngoingStubbing
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Name, Retrieval, v2, ~}

import java.util.Base64
import scala.concurrent.Future

trait AuthSupport { this: TestSupport =>
  private lazy val roles = {
    val base64EncodedRoles =
      fakeApplication.configuration.get[Seq[String]]("stride.base64-encoded-roles")
    base64EncodedRoles.map(x => new String(Base64.getDecoder.decode(x)))
  }

  private lazy val secureRoles = {
    val base64SecureValues =
      fakeApplication.configuration.get[Seq[String]]("stride.base64-encoded-secure-roles")
    base64SecureValues.map(x => new String(Base64.getDecoder.decode(x)))
  }

  type RetrievalsType = Enrolments ~ Option[Credentials] ~ Option[Name] ~ Option[String]
  val retrievals = new ~(
    new ~(
      new ~(Enrolments(roles.map(Enrolment(_)).toSet), Some(Credentials("PID", "pidType"))),
      Some(Name(Some("name"), None))
    ),
    Some("email")
  )
  val secureRetrievals = new ~(
    new ~(
      new ~(Enrolments(secureRoles.map(Enrolment(_)).toSet), Some(Credentials("PID", "pidType"))),
      Some(Name(Some("name"), None))
    ),
    Some("email")
  )

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuthorised[A](expectedPredicate: Predicate, expectedRetrieval: Retrieval[A])(
    result: Either[Throwable, A]
  ): OngoingStubbing[Future[A]] =
    whenMock(
      mockAuthConnector
        .authorise(eqTo(expectedPredicate), eqTo(expectedRetrieval))(any(), any())
    ).thenReturn(result.fold(Future.failed, Future.successful))

  def mockSuccessfulAuthorisation(): OngoingStubbing[Future[Enrolments]] =
    mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(
      Right(Enrolments(roles.map(Enrolment(_)).toSet))
    )

  def mockSuccessfulSecureAuthorisation(): OngoingStubbing[Future[Enrolments]] =
    mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(
      Right(Enrolments(secureRoles.map(Enrolment(_)).toSet))
    )

  def mockSuccessfulAuthorisationWithDetails()
    : OngoingStubbing[Future[Enrolments ~ Option[Credentials] ~ Option[Name] ~ Option[String]]] =
    mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments and credentials and name and email)(
      Right(retrievals)
    )

  def mockSuccessfulSecureAuthorisationWithDetails()
    : OngoingStubbing[Future[Enrolments ~ Option[Credentials] ~ Option[Name] ~ Option[String]]] =
    mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments and credentials and name and email)(
      Right(secureRetrievals)
    )

  def mockAuthFail(): Unit =
    mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(
      Left(BearerTokenExpired("no login session exists"))
    )
}
