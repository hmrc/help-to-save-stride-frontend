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

package uk.gov.hmrc.helptosavestridefrontend.connectors

import cats.instances.int._
import cats.syntax.eq._
import org.scalatest.EitherValues
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavestridefrontend.models.CreateAccountResult.{AccountAlreadyExists, AccountCreated}
import uk.gov.hmrc.helptosavestridefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavestridefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavestridefrontend.models.{AccountDetails, EnrolmentStatus}
import uk.gov.hmrc.helptosavestridefrontend.util.MockPagerDuty
import uk.gov.hmrc.helptosavestridefrontend.{TestData, TestSupport}
import uk.gov.hmrc.http.client.HttpClientV2

import scala.concurrent.ExecutionContext.Implicits.global

class HelpToSaveConnectorSpec
    extends TestSupport with MockitoSugar with MockPagerDuty with ScalaCheckDrivenPropertyChecks with TestData
    with EitherValues {
  val mockHttp: HttpClientV2 = fakeApplication.injector.instanceOf[HttpClientV2]
  val connector =
    new HelpToSaveConnectorImpl(mockHttp, mockMetrics, mockPagerDuty, configuration, servicesConfig, environment)

  private val eligibilityUrl: String = "/help-to-save/eligibility-check"
  private val payePersonalDetailsUrl: String = "/help-to-save/paye-personal-details"
  private val createAccountUrl: String = "/help-to-save/create-account"
  private val enrolmentStatusUrl: String = "/help-to-save/enrolment-status"

  private def getAccountUrl(nino: String): String = s"/help-to-save/$nino/account"

  private val emptyBody = ""

  val connectorCallFailureMessage: (HTTPMethod, String) => String = (httpMethod, urlPath) =>
    s"$httpMethod of 'http://$wireMockHost:$wireMockPort$urlPath' failed. Caused by: 'Connection refused"

  "HelpToSaveConnector" when {
    "checking eligibility" must {
      def eligibilityCheckResponse(resultCode: Int): JsValue =
        JsObject(
          Map(
            "eligibilityCheckResult" -> Json.toJson(EligibilityCheckResponse("eligible", resultCode, "Tax credits", 1))
          )
        )

      "return a successful eligibility response for a valid NINO" in {
        when(GET, eligibilityUrl, Map("nino" -> nino)).thenReturn(OK, eligibilityCheckResponse(1))

        await(connector.getEligibility(nino).value) shouldBe Right(
          Eligible(EligibilityCheckResponse("eligible", 1, "Tax credits", 1))
        )
      }

      "handles the case of success response but user is not eligible" in {
        when(GET, eligibilityUrl, Map("nino" -> nino)).thenReturn(200, eligibilityCheckResponse(2))

        await(connector.getEligibility(nino).value) shouldBe Right(
          Ineligible(EligibilityCheckResponse("eligible", 2, "Tax credits", 1))
        )
      }

      "handles the case of success response but user has hot an account already" in {
        when(GET, eligibilityUrl, Map("nino" -> nino)).thenReturn(200, eligibilityCheckResponse(3))

        await(connector.getEligibility(nino).value) shouldBe Right(
          AlreadyHasAccount(EligibilityCheckResponse("eligible", 3, "Tax credits", 1))
        )
      }

      "handles the case of success response but invalid eligibility result code" in {
        (4 to 10).foreach { resultCode =>
          when(GET, eligibilityUrl, Map("nino" -> nino)).thenReturn(200, eligibilityCheckResponse(resultCode))

          mockPagerDutyAlert("Could not parse JSON in eligibility check response")

          await(connector.getEligibility(nino).value).isLeft shouldBe true
        }
      }

      "handles the case of success response but no eligibility result json" in {
        when(GET, eligibilityUrl, Map("nino" -> nino)).thenReturn(200, Json.parse("{}"))

        mockPagerDutyAlert("Could not parse JSON in eligibility check response")

        await(connector.getEligibility(nino).value).isLeft shouldBe true
      }

      "handle responses when they contain invalid json" in {
        when(GET, eligibilityUrl, Map("nino" -> nino)).thenReturn(200, Json.parse("""{"invalid": "foo"}"""))

        mockPagerDutyAlert("Could not parse JSON in eligibility check response")
        await(connector.getEligibility(nino).value).isLeft shouldBe true
      }

      "return with an error" when {
        "the call fails" in {
          when(GET, eligibilityUrl, Map("nino" -> nino))
          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Failed to make call to check eligibility")

          await(connector.getEligibility(nino).value).isLeft shouldBe true
        }

        "the call comes back with an unexpected http status" in {
          forAll { (status: Int) =>
            whenever(status > 0 && status =!= 200) {
              when(GET, eligibilityUrl, Map("nino" -> nino)).thenReturn(status, emptyBody)

              // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
              mockPagerDutyAlert("Failed to make call to check eligibility")

              await(connector.getEligibility(nino).value).isLeft shouldBe true
            }
          }
        }
      }
    }

    "getting paye-personal-details and converting to nsi-user-info" must {
      "return a successful paye-details response for a valid NINO and convert to nsi-user-info" in {
        when(GET, payePersonalDetailsUrl, Map("nino" -> nino)).thenReturn(200, Json.parse(payeDetailsJson))

        await(connector.getNSIUserInfo(nino).value) shouldBe Right(nsiUserInfo)
      }

      "handle responses when they contain invalid json" in {
        when(GET, payePersonalDetailsUrl, Map("nino" -> nino)).thenReturn(200, Json.parse("""{"invalid": "foo"}"""))

        mockPagerDutyAlert("Could not parse JSON in the paye-personal-details response")
        await(connector.getNSIUserInfo(nino).value).isLeft shouldBe true
      }

      "handle responses when they contain empty json" in {
        when(GET, payePersonalDetailsUrl, Map("nino" -> nino)).thenReturn(200, Json.parse("""{}"""))

        mockPagerDutyAlert("Could not parse JSON in the paye-personal-details response")
        await(connector.getNSIUserInfo(nino).value).isLeft shouldBe true
      }

      "return with an error" when {
        "the call fails" in {
          when(GET, payePersonalDetailsUrl, Map("nino" -> nino))

          // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
          mockPagerDutyAlert("Failed to make call to paye-personal-details")

          await(connector.getNSIUserInfo(nino).value).isLeft shouldBe true
        }

        "the call comes back with an unexpected http status" in {
          forAll { (status: Int) =>
            whenever(status > 0 && status =!= 200 && status =!= 404) {
              when(GET, payePersonalDetailsUrl, Map("nino" -> nino)).thenReturn(status, emptyBody)

              // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
              mockPagerDutyAlert("Failed to make call to paye-personal-details")

              await(connector.getNSIUserInfo(nino).value).isLeft shouldBe true
            }
          }
        }
      }
    }

    "createAccount" must {
      val createAccountRequest = CreateAccountRequest(nsiUserInfo, 7, "Stride", detailsManuallyEntered = false)

      "return a CreateAccountResult of AccountCreated when the proxy returns 201" in {
        when(POST, createAccountUrl, body = Some(Json.toJson(createAccountRequest).toString()))
          .thenReturn(CREATED, Json.parse("""{"accountNumber":"123456789"}"""))

        val result = await(connector.createAccount(createAccountRequest).value)
        result shouldBe Right(AccountCreated("123456789"))
      }

      "return an error if the createAccount returns 201 but with invalid or no accountNumber json body" in {
        when(POST, createAccountUrl, body = Some(Json.toJson(createAccountRequest).toString()))
          .thenReturn(CREATED, Json.parse("""{"blah":"blah"}"""))

        mockPagerDutyAlert("createAccount returned 201 but couldn't parse the accountNumber from response body")
        val result = await(connector.createAccount(createAccountRequest).value)
        result.isLeft shouldBe true
      }

      "return a CreateAccountResult of AccountAlreadyExists when the proxy returns 409" in {
        when(POST, createAccountUrl, body = Some(Json.toJson(createAccountRequest).toString()))
          .thenReturn(CONFLICT, Json.parse("{}"))

        val result = await(connector.createAccount(createAccountRequest).value)
        result shouldBe Right(AccountAlreadyExists)
      }

      "return a Left when the proxy returns a status other than 201 or 409" in {
        when(POST, createAccountUrl, body = Some(Json.toJson(createAccountRequest).toString()))
          .thenReturn(BAD_REQUEST, Json.parse("{}"))

        mockPagerDutyAlert("Received unexpected http status from the back end when calling the create account url")
        val result = await(connector.createAccount(createAccountRequest).value)
        result shouldBe Left("createAccount returned a status other than 201, and 409, status was: 400")
      }
    }

    "failure to create account on server unavailability" must {
      val createAccountRequest = CreateAccountRequest(nsiUserInfo, 7, "Stride", detailsManuallyEntered = false)

      "return a Left with future fails" in {
        wireMockServer.stop()
        when(POST, createAccountUrl, body = Some(Json.toJson(createAccountRequest).toString()))

        mockPagerDutyAlert("Failed to make call to the back end create account url")
        val result = await(connector.createAccount(createAccountRequest).value)
        result.left.value should include(
          s"Encountered error while trying to make createAccount call, with message: " +
            s"${connectorCallFailureMessage(POST, "/help-to-save/create-account")}"
        )
        wireMockServer.start()
      }
    }

    "getEnrolmentStatus" must {
      val statusToJSON: Map[EnrolmentStatus, String] = Map(
        Enrolled ->
          """
            |{
            |  "enrolled"    : true,
            |  "itmpHtSFlag" : true
            |}
          """.stripMargin,
        Enrolled ->
          """
            |{
            |  "enrolled"    : true,
            |  "itmpHtSFlag" : false
            |}
          """.stripMargin,
        NotEnrolled ->
          """
            |{
            |  "enrolled"    : false,
            |  "itmpHtSFlag" : false
            |}
          """.stripMargin
      )

      "return the enrolment status" in {
        statusToJSON.foreach { case (s, j) =>
          when(GET, enrolmentStatusUrl, Map("nino" -> nino)).thenReturn(OK, Json.parse(j))

          val result = connector.getEnrolmentStatus(nino)
          await(result.value) shouldBe Right(s)
        }
      }

      "return an error" when {
        "there is no JSON" in {
          when(GET, enrolmentStatusUrl, Map("nino" -> nino)).thenReturn(OK, "200")

          val result = connector.getEnrolmentStatus(nino)
          await(result.value).isLeft shouldBe true
        }

        "there is unexpected JSON" in {
          when(GET, enrolmentStatusUrl, Map("nino" -> nino)).thenReturn(OK, Json.parse("""{ "a" : 1 }"""))

          val result = connector.getEnrolmentStatus(nino)
          await(result.value).isLeft shouldBe true
        }

        "the response comes back with any status other than 200" in {
          forAll { (status: Int) =>
            whenever(status > 0 && status =!= 200) {
              statusToJSON.foreach { case (_, j) =>
                when(GET, enrolmentStatusUrl, Map("nino" -> nino)).thenReturn(status, Json.parse(j))

                mockPagerDutyAlert(
                  "Received unexpected http status from the back end when calling the get enrolment status url"
                )

                val result = connector.getEnrolmentStatus(nino)
                await(result.value).isLeft shouldBe true
              }
            }
          }
        }
      }
    }

    "getAccount" must {
      val accountNumber = "12345678"
      val correlationId = "test-correlation-id"
      val validJSON = Json.parse(s"""{ "accountNumber" : "$accountNumber"}""")
      val paramemters = Map("systemId" -> "MDTP-STRIDE", "correlationId" -> correlationId)

      "return the account details" in {
        when(GET, getAccountUrl(nino), paramemters).thenReturn(OK, validJSON)

        await(connector.getAccount(nino, correlationId).value) shouldBe Right(AccountDetails(accountNumber))
      }

      "return an error" when {
        "the response comes back with any status other than 200" in {
          forAll { (status: Int) =>
            whenever(status > 0 && status =!= 200) {
              when(GET, getAccountUrl(nino), paramemters).thenReturn(status, validJSON)

              await(connector.getAccount(nino, correlationId).value).isLeft shouldBe true
            }
          }
        }

        "the JSON in the response cannot be parsed" in {
          when(GET, getAccountUrl(nino), paramemters).thenReturn(OK, Json.parse("""{}"""))
          await(connector.getAccount(nino, correlationId).value).isLeft shouldBe true
        }
      }
    }

    "failure to get account when server unavailable" must {
      "return a Left when future fails" in {
        wireMockServer.stop()
        when(GET, enrolmentStatusUrl, Map("nino" -> nino))
        mockPagerDutyAlert("Failed to make call to the back end get enrolment status url")

        val result = connector.getEnrolmentStatus(nino)
        await(result.value).isLeft shouldBe true

        wireMockServer.start()
      }
    }
  }
}
