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

package uk.gov.hmrc.helptosavestridefrontend.connectors

import cats.instances.int._
import cats.syntax.eq._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.libs.json.{Json, Writes}
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavestridefrontend.models.CreateAccountResult.{AccountAlreadyExists, AccountCreated}
import uk.gov.hmrc.helptosavestridefrontend.models.EnrolmentStatus
import uk.gov.hmrc.helptosavestridefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavestridefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavestridefrontend.util.MockPagerDuty
import uk.gov.hmrc.helptosavestridefrontend.{TestData, TestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

class HelpToSaveConnectorSpec extends TestSupport with MockPagerDuty with GeneratorDrivenPropertyChecks with TestData {

  val connector = new HelpToSaveConnectorImpl(mockHttp, mockMetrics, mockPagerDuty, configuration, environment)

  def mockGet(url: String)(response: Option[HttpResponse]) =
    (mockHttp.get(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *)
      .returning(response.fold(Future.failed[HttpResponse](new Exception("")))(Future.successful))

  def mockHttpPost[A](url: String, createAccountRequest: A)(response: Option[HttpResponse]) =
    (mockHttp.post(_: String, _: A, _: Seq[(String, String)])(_: Writes[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, createAccountRequest, Seq.empty[(String, String)], *, *, *)
      .returning(response.fold(Future.failed[HttpResponse](new Exception("")))(Future.successful))

  "HelpToSaveConnector" when {

    "checking eligibility" must {
        def eligibilityCheckResponse(resultCode: Int) = EligibilityCheckResponse("eligible", resultCode, "Tax credits", 1)

      "return a successful eligibility response for a valid NINO" in {
        mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(200, Some(Json.toJson(eligibilityCheckResponse(1)))))) // scalastyle:ignore magic.number
        await(connector.getEligibility(nino).value) shouldBe Right(
          Eligible(EligibilityCheckResponse("eligible", 1, "Tax credits", 1)))
      }

      "handles the case of success response but user is not eligible" in {
        mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(200, Some(Json.toJson(eligibilityCheckResponse(2)))))) // scalastyle:ignore magic.number
        await(connector.getEligibility(nino).value) shouldBe Right(
          Ineligible(EligibilityCheckResponse("eligible", 2, "Tax credits", 1)))
      }

      "handles the case of success response but user has hot an account already" in {
        mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(200, Some(Json.toJson(eligibilityCheckResponse(3)))))) // scalastyle:ignore magic.number
        await(connector.getEligibility(nino).value) shouldBe Right(
          AlreadyHasAccount(EligibilityCheckResponse("eligible", 3, "Tax credits", 1)))
      }

      "handles the case of success response but invalid eligibility result code" in {
        (4 to 10).foreach{ resultCode ⇒
          mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(200, Some(Json.toJson(eligibilityCheckResponse(resultCode)))))) // scalastyle:ignore magic.number
          mockPagerDutyAlert("Could not parse JSON in eligibility check response")

          await(connector.getEligibility(nino).value).isLeft shouldBe true
        }

      }

      "handles the case of success response but no eligibility result json" in {
        inSequence{
          mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(200, Some(Json.parse("{}"))))) // scalastyle:ignore magic.number
          mockPagerDutyAlert("Could not parse JSON in eligibility check response")
        }

        await(connector.getEligibility(nino).value).isLeft shouldBe true
      }

      "handle responses when they contain invalid json" in {
        inSequence {
          mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(
            200, Some(Json.parse("""{"invalid": "foo"}"""))))) // scalastyle:ignore magic.number
          mockPagerDutyAlert("Could not parse JSON in eligibility check response")
        }
        await(connector.getEligibility(nino).value).isLeft shouldBe true
      }

      "return with an error" when {
        "the call fails" in {
          inSequence {
            mockGet(connector.eligibilityUrl(nino))(None)
            // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
            mockPagerDutyAlert("Failed to make call to check eligibility")
          }

          await(connector.getEligibility(nino).value).isLeft shouldBe true
        }

        "the call comes back with an unexpected http status" in {
          forAll { status: Int ⇒
            whenever(status > 0 && status =!= 200) {
              inSequence {
                mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(status)))
                // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
                mockPagerDutyAlert("Failed to make call to check eligibility")
              }

              await(connector.getEligibility(nino).value).isLeft shouldBe true
            }

          }
        }

      }
    }

    "getting paye-personal-details and converting to nsi-user-info" must {

      "return a successful paye-details response for a valid NINO and convert to nsi-user-info" in {
        mockGet(connector.payePersonalDetailsUrl(nino))(Some(HttpResponse(200, Some(Json.parse(payeDetailsJson))))) // scalastyle:ignore magic.number
        await(connector.getNSIUserInfo(nino).value) shouldBe Right(nsiUserInfo)
      }

      "handle responses when they contain invalid json" in {
        mockGet(connector.payePersonalDetailsUrl(nino))(Some(HttpResponse(200, Some(Json.parse("""{"invalid": "foo"}"""))))) // scalastyle:ignore magic.number
        mockPagerDutyAlert("Could not parse JSON in the paye-personal-details response")
        await(connector.getNSIUserInfo(nino).value).isLeft shouldBe true
      }

      "handle responses when they contain empty json" in {
        mockGet(connector.payePersonalDetailsUrl(nino))(Some(HttpResponse(200, Some(Json.parse("""{}"""))))) // scalastyle:ignore magic.number
        mockPagerDutyAlert("Could not parse JSON in the paye-personal-details response")
        await(connector.getNSIUserInfo(nino).value).isLeft shouldBe true
      }

      "return with an error" when {
        "the call fails" in {
          inSequence {
            mockGet(connector.payePersonalDetailsUrl(nino))(None)
            // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
            mockPagerDutyAlert("Failed to make call to paye-personal-details")
          }

          await(connector.getNSIUserInfo(nino).value).isLeft shouldBe true
        }

        "the call comes back with an unexpected http status" in {
          forAll { status: Int ⇒
            whenever(status > 0 && status =!= 200 && status =!= 404) {
              inSequence {
                mockGet(connector.payePersonalDetailsUrl(nino))(Some(HttpResponse(status)))
                // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
                mockPagerDutyAlert("Failed to make call to paye-personal-details")
              }

              await(connector.getNSIUserInfo(nino).value).isLeft shouldBe true
            }

          }

        }

      }

    }

    "createAccount" must {

      val url = "http://localhost:7001/help-to-save/create-account"

      val createAccountRequest = CreateAccountRequest(nsiUserInfo, 7)

      "return a CreateAccountResult of AccountCreated when the proxy returns 201" in {
        mockHttpPost(url, createAccountRequest)(Some(HttpResponse(CREATED)))

        val result = await(connector.createAccount(createAccountRequest).value)
        result shouldBe Right(AccountCreated)
      }

      "return a CreateAccountResult of AccountAlreadyExists when the proxy returns 409" in {
        mockHttpPost(url, createAccountRequest)(Some(HttpResponse(CONFLICT)))

        val result = await(connector.createAccount(createAccountRequest).value)
        result shouldBe Right(AccountAlreadyExists)
      }

      "return a Left when the proxy returns a status other than 201 or 409" in {
        inSequence {
          mockHttpPost(url, createAccountRequest)(Some(HttpResponse(BAD_REQUEST)))
          mockPagerDutyAlert("Received unexpected http status from the back end when calling the create account url")
        }
        val result = await(connector.createAccount(createAccountRequest).value)
        result shouldBe Left("createAccount returned a status other than 201, and 409, status was: 400 with response body: null")
      }

      "return a Left when the future fails" in {
        inSequence {
          mockHttpPost(url, createAccountRequest)(None)
          mockPagerDutyAlert("Failed to make call to the back end create account url")
        }
        val result = await(connector.createAccount(createAccountRequest).value)
        result shouldBe Left("Encountered error while trying to make createAccount call, with message: ")
      }
    }

    "getEnrolmentStatus" must {
      val statusToJSON: Map[EnrolmentStatus, String] = Map(
        Enrolled →
          """
            |{
            |  "enrolled"    : true,
            |  "itmpHtSFlag" : true
            |}
          """.stripMargin,
        Enrolled →
          """
            |{
            |  "enrolled"    : true,
            |  "itmpHtSFlag" : false
            |}
          """.stripMargin,
        NotEnrolled →
          """
            |{
            |  "enrolled"    : false,
            |  "itmpHtSFlag" : false
            |}
          """.stripMargin
      )

      "return the enrolment status" in {
        statusToJSON.foreach{
          case (s, j) ⇒
            mockGet(connector.enrolmentStatusUrl(nino))(Some(HttpResponse(200, Some(Json.parse(j)))))

            val result = connector.getEnrolmentStatus(nino)
            await(result.value) shouldBe Right(s)
        }

      }

      "return an error" when {
        "there is no JSON" in {
          mockGet(connector.enrolmentStatusUrl(nino))(Some(HttpResponse(200, None)))

          val result = connector.getEnrolmentStatus(nino)
          await(result.value).isLeft shouldBe true
        }

        "there is unexpected JSON" in {
          mockGet(connector.enrolmentStatusUrl(nino))(Some(HttpResponse(200, Some(Json.parse("""{ "a" : 1 }""")))))

          val result = connector.getEnrolmentStatus(nino)
          await(result.value).isLeft shouldBe true
        }

        "the response comes back with any status other than 200" in {
          forAll{ status: Int ⇒
            whenever(status > 0 && status =!= 200) {
              statusToJSON.foreach {
                case (_, j) ⇒
                  inSequence {
                    mockGet(connector.enrolmentStatusUrl(nino))(Some(HttpResponse(status, Some(Json.parse(j)))))
                    mockPagerDutyAlert("Received unexpected http status from the back end when calling the get enrolment status url")
                  }

                  val result = connector.getEnrolmentStatus(nino)
                  await(result.value).isLeft shouldBe true

              }
            }
          }

        }

        "the future fails" in {
          inSequence {
            mockGet(connector.enrolmentStatusUrl(nino))(None)
            mockPagerDutyAlert("Failed to make call to the back end get enrolment status url")
          }

          val result = connector.getEnrolmentStatus(nino)
          await(result.value).isLeft shouldBe true
        }
      }

    }

  }

}
