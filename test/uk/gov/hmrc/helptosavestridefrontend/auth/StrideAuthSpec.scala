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

import play.api.Configuration
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals._
import uk.gov.hmrc.helptosavestridefrontend.TestSupport
import uk.gov.hmrc.helptosavestridefrontend.auth.StrideAuthSpec.NotLoggedInException
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.controllers.StrideFrontendController
import uk.gov.hmrc.helptosavestridefrontend.models.{OperatorDetails, RoleType}
import uk.gov.hmrc.http.HeaderCarrier

import java.net.URLEncoder
import java.util.Base64
import scala.concurrent.{ExecutionContext, Future}

class StrideAuthSpec extends TestSupport {

  lazy val standardRoles = List("a", "b")
  lazy val secureRoles = List("c", "d")

  def base64Encode(s: String): String = new String(Base64.getEncoder.encode(s.getBytes()))

  override lazy val additionalConfig: Configuration = Configuration(
    "stride.base64-encoded-roles"        -> standardRoles.map(base64Encode),
    "stride.base64-encoded-secure-roles" -> secureRoles.map(base64Encode)
  )

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuthorised[A](expectedPredicate: Predicate, expectedRetrieval: Retrieval[A])(result: Either[Throwable, A]) =
    (mockAuthConnector
      .authorise(_: Predicate, _: Retrieval[A])(_: HeaderCarrier, _: ExecutionContext))
      .expects(expectedPredicate, expectedRetrieval, *, *)
      .returning(result.fold(Future.failed, Future.successful))

  class TestStrideAuth(val frontendAppConfig: FrontendAppConfig)
      extends StrideFrontendController(testMcc, errorHandler) with StrideAuth {

    val authConnector: AuthConnector = mockAuthConnector

  }

  lazy val test = new TestStrideAuth(frontendAppConfig)

  lazy val action = test.authorisedFromStride { _ => _ =>
    Future.successful(Ok)
  }(controllers.routes.Default.redirect())

  "StrideAuth" must {

    "provide a authorised method" which {

      "redirects to the stride login page if the requester is not logged in" in {
        implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
        mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(Left(NotLoggedInException))

        val result = action(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(
          test.strideLoginUrl + s"?successURL=${URLEncoder.encode(controllers.routes.Default.redirect().absoluteURL(), "UTF-8")}&origin=help-to-save-stride-frontend"
        )
      }

      "returns an Unauthorised status" when {

        "the requester does not have any of the necessary roles" in {
          List(
            Set("aa"),
            Set("aa", "bb"),
            Set.empty
          ).foreach { enrolments =>
            withClue(s"For enrolments $enrolments:") {
              mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(
                Right(Enrolments(enrolments.map(Enrolment(_))))
              )

              status(action(FakeRequest())) shouldBe UNAUTHORIZED
            }
          }
        }
      }

      "allow authorised logic to be run if the requester has the correct standard roles" in {
        List(
          Set("a"),
          Set("b"),
          Set("a", "b")
        ).foreach { enrolments =>
          withClue(s"For enrolments $enrolments:") {
            mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(
              Right(Enrolments(enrolments.map(Enrolment(_))))
            )
            status(action(FakeRequest())) shouldBe OK
          }
        }
      }

      "should return standard stride operator details when requested" in {
        val retrievals = new ~(
          new ~(
            new ~(Enrolments(standardRoles.map(Enrolment(_)).toSet), Some(Credentials("PID", "pidType"))),
            Some(Name(Some("name"), None))
          ),
          Some("email")
        )

        mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments and credentials and name and email)(
          Right(retrievals)
        )
        val action = test.authorisedFromStrideWithDetails { _ => operatorDetails => roleType =>
          {
            operatorDetails shouldBe OperatorDetails(List("a", "b"), Some("PID"), "name", "email")
            roleType shouldBe RoleType.Standard(List("a", "b"))
            Future.successful(Ok)
          }
        }(controllers.routes.Default.redirect())

        status(action(FakeRequest())) shouldBe OK
      }

      "return the Secure RoleType given the secure enrolment when using authorisedFromStride" in {
        List(
          Set("c"),
          Set("d"),
          Set("c", "d")
        ).foreach { enrolments =>
          withClue(s"For enrolments $enrolments:") {
            mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(
              Right(Enrolments(enrolments.map(Enrolment(_))))
            )

            val action = test.authorisedFromStride { _ => roleType =>
              {
                roleType shouldBe RoleType.Secure(enrolments.toList)
                Future.successful(Ok)
              }
            }(controllers.routes.Default.redirect())

            status(action(FakeRequest())) shouldBe OK
          }
        }
      }

      "return the Secure RoleType given the secure enrolment when using authorisedFromStrideWithDetails" in {
        val retrievals = new ~(
          new ~(
            new ~(Enrolments(secureRoles.map(Enrolment(_)).toSet), Some(Credentials("PID", "pidType"))),
            Some(Name(Some("name"), None))
          ),
          Some("email")
        )

        mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments and credentials and name and email)(
          Right(retrievals)
        )

        val action = test.authorisedFromStrideWithDetails { _ => operatorDetails => roleType =>
          {
            operatorDetails shouldBe OperatorDetails(List("c", "d"), Some("PID"), "name", "email")
            roleType shouldBe RoleType.Secure(List("c", "d"))
            Future.successful(Ok)
          }
        }(controllers.routes.Default.redirect())

        status(action(FakeRequest())) shouldBe OK
      }

    }
  }

}

object StrideAuthSpec {

  case object NotLoggedInException extends NoActiveSession("uh oh")

}
