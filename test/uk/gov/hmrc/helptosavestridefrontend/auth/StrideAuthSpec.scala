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

package uk.gov.hmrc.helptosavestridefrontend.auth

import java.net.URLEncoder
import java.util.Base64

import play.api.Configuration
import play.api.mvc.AnyContentAsEmpty
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthProvider.PrivilegedApplication
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, _}
import uk.gov.hmrc.helptosavestridefrontend.TestSupport
import uk.gov.hmrc.helptosavestridefrontend.auth.StrideAuthSpec.NotLoggedInException
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.models.OperatorDetails
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.FrontendController

import scala.concurrent.{ExecutionContext, Future}

class StrideAuthSpec extends TestSupport {

  val roles = List("a", "b")
  val base64EncodedRoles = roles.map(r ⇒ new String(Base64.getEncoder.encode(r.getBytes)))

  override lazy val additionalConfig: Configuration = Configuration("stride.base64-encoded-roles" → base64EncodedRoles)

  val mockAuthConnector: AuthConnector = mock[AuthConnector]

  def mockAuthorised[A](expectedPredicate: Predicate,
                        expectedRetrieval: Retrieval[A])(result: Either[Throwable, A]) =
    (mockAuthConnector.authorise(_: Predicate, _: Retrieval[A])(_: HeaderCarrier, _: ExecutionContext))
      .expects(expectedPredicate, expectedRetrieval, *, *)
      .returning(result.fold(Future.failed, Future.successful))

  class TestStrideAuth(roles:                 List[String],
                       val frontendAppConfig: FrontendAppConfig) extends StrideAuth with FrontendController {

    val authConnector: AuthConnector = mockAuthConnector

  }

  "StrideAuth" must {

    lazy val test = new TestStrideAuth(roles, frontendAppConfig)

    lazy val action = test.authorisedFromStride { _ ⇒ Ok }(controllers.routes.Default.redirect())

    "provide a authorised method" which {

      "redirects to the stride login page if the requester is not logged in" in {
        implicit val request: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()
        mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(Left(NotLoggedInException))

        val result = action(request)
        status(result) shouldBe SEE_OTHER
        redirectLocation(result) shouldBe Some(test.strideLoginUrl + s"?successURL=${
          URLEncoder.encode(
            controllers.routes.Default.redirect().absoluteURL(), "UTF-8")
        }&origin=help-to-save-stride-frontend")
      }

      "returns an Unauthorised status" when {

        "the requester does not have the necessary roles" in {
          List(
            Set("a"),
            Set("b"),
            Set("c"),
            Set.empty
          ).foreach { enrolments ⇒
              mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(Right(Enrolments(enrolments.map(Enrolment(_)))))

              status(action(FakeRequest())) shouldBe UNAUTHORIZED
            }
        }
      }

      "allow authorised logic to be run if the requester has the correct roles" in {
        mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments)(Right(Enrolments(roles.map(Enrolment(_)).toSet)))
        status(action(FakeRequest())) shouldBe OK
      }

      "should return stride operator details when requested" in {
        val retrievals = new ~(new ~(new ~(Enrolments(roles.map(Enrolment(_)).toSet), Credentials("PID", "pidType")), Name(Some("name"), None)), Some("email"))
        mockAuthorised(AuthProviders(PrivilegedApplication), allEnrolments and credentials and name and email)(Right(retrievals))
        val action = test.authorisedFromStrideWithDetails { _ ⇒ operatorDetails ⇒ {
          operatorDetails shouldBe OperatorDetails(List("a", "b"), "PID", "name", "email")
          Ok
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

