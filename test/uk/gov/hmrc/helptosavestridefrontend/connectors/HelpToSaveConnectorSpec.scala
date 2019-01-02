/*
 * Copyright 2019 HM Revenue & Customs
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
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.helptosavestridefrontend.models.CreateAccountResult.{AccountAlreadyExists, AccountCreated}
import uk.gov.hmrc.helptosavestridefrontend.models.{AccountDetails, EnrolmentStatus}
import uk.gov.hmrc.helptosavestridefrontend.models.EnrolmentStatus.{Enrolled, NotEnrolled}
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavestridefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavestridefrontend.util.MockPagerDuty
import uk.gov.hmrc.helptosavestridefrontend.{TestData, TestSupport}
import uk.gov.hmrc.http.HttpResponse

class HelpToSaveConnectorSpec extends TestSupport with MockPagerDuty with GeneratorDrivenPropertyChecks with TestData with HttpSupport {

  val connector = new HelpToSaveConnectorImpl(mockHttp, mockMetrics, mockPagerDuty, configuration, environment)

  private val eligibilityUrl: String = "http://localhost:7001/help-to-save/stride/eligibility-check"
  private val payePersonalDetailsUrl: String = "http://localhost:7001/help-to-save/stride/paye-personal-details"
  private val createAccountUrl: String = "http://localhost:7001/help-to-save/create-account"
  private val enrolmentStatusUrl: String = "http://localhost:7001/help-to-save/stride/enrolment-status"
  private def getAccountUrl(nino: String): String = s"http://localhost:7001/help-to-save/$nino/account"
  private val emptyQueryParameters: Map[String, String] = Map.empty[String, String]

  "HelpToSaveConnector" when {

    "checking eligibility" must {
        def eligibilityCheckResponse(resultCode: Int): JsValue =
          JsObject(Map("eligibilityCheckResult" → Json.toJson(EligibilityCheckResponse("eligible", resultCode, "Tax credits", 1))))

      "return a successful eligibility response for a valid NINO" in {
        mockGet(eligibilityUrl, Map("nino" -> nino))(Some(HttpResponse(200, Some(eligibilityCheckResponse(1))))) // scalastyle:ignore magic.number

        await(connector.getEligibility(nino).value) shouldBe Right(
          Eligible(EligibilityCheckResponse("eligible", 1, "Tax credits", 1)))
      }

      "handles the case of success response but user is not eligible" in {
        mockGet(eligibilityUrl, Map("nino" -> nino))(Some(HttpResponse(200, Some(eligibilityCheckResponse(2))))) // scalastyle:ignore magic.number
        await(connector.getEligibility(nino).value) shouldBe Right(
          Ineligible(EligibilityCheckResponse("eligible", 2, "Tax credits", 1)))
      }

      "handles the case of success response but user has hot an account already" in {
        mockGet(eligibilityUrl, Map("nino" -> nino))(Some(HttpResponse(200, Some(eligibilityCheckResponse(3))))) // scalastyle:ignore magic.number
        await(connector.getEligibility(nino).value) shouldBe Right(
          AlreadyHasAccount(EligibilityCheckResponse("eligible", 3, "Tax credits", 1)))
      }

      "handles the case of success response but invalid eligibility result code" in {
        (4 to 10).foreach { resultCode ⇒
          mockGet(eligibilityUrl, Map("nino" -> nino))(Some(HttpResponse(200, Some(eligibilityCheckResponse(resultCode))))) // scalastyle:ignore magic.number
          mockPagerDutyAlert("Could not parse JSON in eligibility check response")

          await(connector.getEligibility(nino).value).isLeft shouldBe true
        }

      }

      "handles the case of success response but no eligibility result json" in {
        inSequence {
          mockGet(eligibilityUrl, Map("nino" -> nino))(Some(HttpResponse(200, Some(Json.parse("{}"))))) // scalastyle:ignore magic.number
          mockPagerDutyAlert("Could not parse JSON in eligibility check response")
        }

        await(connector.getEligibility(nino).value).isLeft shouldBe true
      }

      "handle responses when they contain invalid json" in {
        inSequence {
          mockGet(eligibilityUrl, Map("nino" -> nino))(Some(HttpResponse(
            200, Some(Json.parse("""{"invalid": "foo"}"""))))) // scalastyle:ignore magic.number
          mockPagerDutyAlert("Could not parse JSON in eligibility check response")
        }
        await(connector.getEligibility(nino).value).isLeft shouldBe true
      }

      "return with an error" when {
        "the call fails" in {
          inSequence {
            mockGet(eligibilityUrl, Map("nino" -> nino))(None)
            // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
            mockPagerDutyAlert("Failed to make call to check eligibility")
          }

          await(connector.getEligibility(nino).value).isLeft shouldBe true
        }

        "the call comes back with an unexpected http status" in {
          forAll { status: Int ⇒
            whenever(status > 0 && status =!= 200) {
              inSequence {
                mockGet(eligibilityUrl, Map("nino" -> nino))(Some(HttpResponse(status)))
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
        mockGet(payePersonalDetailsUrl, Map("nino" -> nino))(Some(HttpResponse(200, Some(Json.parse(payeDetailsJson))))) // scalastyle:ignore magic.number
        await(connector.getNSIUserInfo(nino).value) shouldBe Right(nsiUserInfo)
      }

      "handle responses when they contain invalid json" in {
        mockGet(payePersonalDetailsUrl, Map("nino" -> nino))(Some(HttpResponse(200, Some(Json.parse("""{"invalid": "foo"}"""))))) // scalastyle:ignore magic.number
        mockPagerDutyAlert("Could not parse JSON in the paye-personal-details response")
        await(connector.getNSIUserInfo(nino).value).isLeft shouldBe true
      }

      "handle responses when they contain empty json" in {
        mockGet(payePersonalDetailsUrl, Map("nino" -> nino))(Some(HttpResponse(200, Some(Json.parse("""{}"""))))) // scalastyle:ignore magic.number
        mockPagerDutyAlert("Could not parse JSON in the paye-personal-details response")
        await(connector.getNSIUserInfo(nino).value).isLeft shouldBe true
      }

      "return with an error" when {
        "the call fails" in {
          inSequence {
            mockGet(payePersonalDetailsUrl, Map("nino" -> nino))(None)
            // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
            mockPagerDutyAlert("Failed to make call to paye-personal-details")
          }

          await(connector.getNSIUserInfo(nino).value).isLeft shouldBe true
        }

        "the call comes back with an unexpected http status" in {
          forAll { status: Int ⇒
            whenever(status > 0 && status =!= 200 && status =!= 404) {
              inSequence {
                mockGet(payePersonalDetailsUrl, Map("nino" -> nino))(Some(HttpResponse(status)))
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

      val createAccountRequest = CreateAccountRequest(nsiUserInfo, 7, "Stride", false)

      "return a CreateAccountResult of AccountCreated when the proxy returns 201" in {
        mockPost(createAccountUrl, emptyQueryParameters, createAccountRequest)(Some(HttpResponse(CREATED, Some(Json.parse("""{"accountNumber":"123456789"}""")))))

        val result = await(connector.createAccount(createAccountRequest).value)
        result shouldBe Right(AccountCreated("123456789"))
      }

      "return an error if the createAccount returns 201 but with invalid or no accountNumber json body" in {
        inSequence {
          mockPost(createAccountUrl, emptyQueryParameters, createAccountRequest)(Some(HttpResponse(CREATED, Some(Json.parse("""{"blah":"blah"}""")))))
          mockPagerDutyAlert("createAccount returned 201 but couldn't parse the accountNumber from response body")
        }
        val result = await(connector.createAccount(createAccountRequest).value)
        result.isLeft shouldBe true
      }

      "return a CreateAccountResult of AccountAlreadyExists when the proxy returns 409" in {
        mockPost(createAccountUrl, emptyQueryParameters, createAccountRequest)(Some(HttpResponse(CONFLICT)))

        val result = await(connector.createAccount(createAccountRequest).value)
        result shouldBe Right(AccountAlreadyExists)
      }

      "return a Left when the proxy returns a status other than 201 or 409" in {
        inSequence {
          mockPost(createAccountUrl, emptyQueryParameters, createAccountRequest)(Some(HttpResponse(BAD_REQUEST)))
          mockPagerDutyAlert("Received unexpected http status from the back end when calling the create account url")
        }
        val result = await(connector.createAccount(createAccountRequest).value)
        result shouldBe Left("createAccount returned a status other than 201, and 409, status was: 400 with response body: null")
      }

      "return a Left when the future fails" in {
        inSequence {
          mockPost(createAccountUrl, emptyQueryParameters, createAccountRequest)(None)
          mockPagerDutyAlert("Failed to make call to the back end create account url")
        }
        val result = await(connector.createAccount(createAccountRequest).value)
        result shouldBe Left("Encountered error while trying to make createAccount call, with message: Test exception message")
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
        statusToJSON.foreach {
          case (s, j) ⇒
            mockGet(enrolmentStatusUrl, Map("nino" -> nino))(Some(HttpResponse(200, Some(Json.parse(j)))))

            val result = connector.getEnrolmentStatus(nino)
            await(result.value) shouldBe Right(s)
        }

      }

      "return an error" when {
        "there is no JSON" in {
          mockGet(enrolmentStatusUrl, Map("nino" -> nino))(Some(HttpResponse(200, None)))

          val result = connector.getEnrolmentStatus(nino)
          await(result.value).isLeft shouldBe true
        }

        "there is unexpected JSON" in {
          mockGet(enrolmentStatusUrl, Map("nino" -> nino))(Some(HttpResponse(200, Some(Json.parse("""{ "a" : 1 }""")))))

          val result = connector.getEnrolmentStatus(nino)
          await(result.value).isLeft shouldBe true
        }

        "the response comes back with any status other than 200" in {
          forAll { status: Int ⇒
            whenever(status > 0 && status =!= 200) {
              statusToJSON.foreach {
                case (_, j) ⇒
                  inSequence {
                    mockGet(enrolmentStatusUrl, Map("nino" -> nino))(Some(HttpResponse(status, Some(Json.parse(j)))))
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
            mockGet(enrolmentStatusUrl, Map("nino" -> nino))(None)
            mockPagerDutyAlert("Failed to make call to the back end get enrolment status url")
          }

          val result = connector.getEnrolmentStatus(nino)
          await(result.value).isLeft shouldBe true
        }
      }

    }

    "getAccount" must {

      val accountNumber = "12345678"
      val validJSON = Json.parse(s"""{ "accountNumber" : "${accountNumber}"}""")

        def mockGetAccount(httpResponse: Option[HttpResponse]): Either[String, AccountDetails] = {
          mockGet(getAccountUrl(nino), Map("systemId" -> "MDTP-STRIDE", "correlationId" → "*"))(httpResponse)
          await(connector.getAccount(nino).value)
        }

      "return the account details" in {
        mockGetAccount(Some(HttpResponse(200, Some(validJSON)))) shouldBe Right(AccountDetails(accountNumber))
      }

      "return an error" when {

        "the response comes back with any status other than 200" in {
          forAll { status: Int ⇒
            whenever(status > 0 && status =!= 200) {
              mockGetAccount(Some(HttpResponse(status, Some(validJSON)))) shouldBe a[Left[_, _]]
            }
          }
        }

        "the JSON in the response cannot be parsed" in {
          mockGetAccount(Some(HttpResponse(200, Some(Json.parse("""{}"""))))) shouldBe a[Left[_, _]]
        }

        "the future fails" in {
          mockGetAccount(None) shouldBe a[Left[_, _]]
        }

      }

    }

  }

}
