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
import play.api.libs.json.Json
import uk.gov.hmrc.helptosavestridefrontend.connectors.HelpToSaveConnector.ECResponseHolder
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResponse
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.EligibilityCheckResult.{AlreadyHasAccount, Eligible, Ineligible}
import uk.gov.hmrc.helptosavestridefrontend.util.MockPagerDuty
import uk.gov.hmrc.helptosavestridefrontend.{TestData, TestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class HelpToSaveConnectorSpec extends TestSupport with MockPagerDuty with GeneratorDrivenPropertyChecks with TestData {

  val connector = new HelpToSaveConnectorImpl(mockHttp, mockMetrics, mockPagerDuty, configuration, environment)

  def mockGet(url: String)(response: Option[HttpResponse]) =
    (mockHttp.get(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *)
      .returning(response.fold(Future.failed[HttpResponse](new Exception("")))(Future.successful))

  "HelpToSaveConnector" when {

    "checking eligibility" must {
        def ecHolder(resultCode: Int) = ECResponseHolder(Some(EligibilityCheckResponse("eligible", resultCode, "Tax credits", 1)))
      val emptyECResponse = EligibilityCheckResponse("No tax credit record found for user's NINO", 2, "", -1)

      "return a successful eligibility response for a valid NINO" in {
        mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(200, Some(Json.toJson(ecHolder(1))))))
        Await.result(connector.getEligibility(nino).value, 5.seconds) shouldBe Right(Eligible(EligibilityCheckResponse("eligible", 1, "Tax credits", 1)))
      }

      "handles the case of success response but user is not eligible" in {
        mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(200, Some(Json.toJson(ecHolder(2))))))
        Await.result(connector.getEligibility(nino).value, 5.seconds) shouldBe Right(Ineligible(EligibilityCheckResponse("eligible", 2, "Tax credits", 1)))
      }

      "handles the case of success response but user has hot an account already" in {
        mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(200, Some(Json.toJson(ecHolder(3))))))
        Await.result(connector.getEligibility(nino).value, 5.seconds) shouldBe Right(AlreadyHasAccount(EligibilityCheckResponse("eligible", 3, "Tax credits", 1)))
      }

      "handles the case of success response but invalid eligibility result code" in {
        mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(200, Some(Json.toJson(ecHolder(5))))))
        mockPagerDutyAlert("Could not parse JSON in eligibility check response")

        Await.result(connector.getEligibility(nino).value, 5.seconds).isLeft shouldBe true
      }

      "handles the case of success response but no eligibility result json" in {
        mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(200, None)))
        mockPagerDutyAlert("Could not parse JSON in eligibility check response")

        Await.result(connector.getEligibility(nino).value, 5.seconds).isLeft shouldBe true
      }

      "handle responses when they contain invalid json" in {
        inSequence {
          mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(200, Some(Json.parse("""{"invalid": "foo"}""")))))
          mockPagerDutyAlert("Could not parse JSON in eligibility check response")
        }
        Await.result(connector.getEligibility(nino).value, 555.seconds).isLeft shouldBe true
      }

      "return with an error" when {
        "the call fails" in {
          inSequence {
            mockGet(connector.eligibilityUrl(nino))(None)
            // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
            mockPagerDutyAlert("Failed to make call to check eligibility")
          }

          Await.result(connector.getEligibility(nino).value, 5.seconds).isLeft shouldBe true
        }

        "the call comes back with an unexpected http status" in {
          forAll { status: Int ⇒
            whenever(status > 0 && status =!= 200 && status =!= 404) {
              inSequence {
                mockGet(connector.eligibilityUrl(nino))(Some(HttpResponse(status)))
                // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
                mockPagerDutyAlert("Failed to make call to check eligibility")
              }

              Await.result(connector.getEligibility(nino).value, 5.seconds).isLeft shouldBe true
            }

          }
        }

      }
    }

    "getting paye-personal-details" must {

      "return a successful paye-details response for a valid NINO" in {
        mockGet(connector.payePersonalDetailsUrl(nino))(Some(HttpResponse(200, Some(Json.parse(payeDetailsJson))))) // scalastyle:ignore magic.number
        Await.result(connector.getPayePersonalDetails(nino).value, 5.seconds) shouldBe Right(ppDetails)
      }

      "handle responses when they contain invalid json" in {
        mockGet(connector.payePersonalDetailsUrl(nino))(Some(HttpResponse(200, Some(Json.parse("""{"invalid": "foo"}"""))))) // scalastyle:ignore magic.number
        mockPagerDutyAlert("Could not parse JSON in the paye-personal-details response")
        Await.result(connector.getPayePersonalDetails(nino).value, 5.seconds).isLeft shouldBe true
      }

      "handle responses when they contain empty json" in {
        mockGet(connector.payePersonalDetailsUrl(nino))(Some(HttpResponse(200, Some(Json.parse("""{}"""))))) // scalastyle:ignore magic.number
        mockPagerDutyAlert("Could not parse JSON in the paye-personal-details response")
        Await.result(connector.getPayePersonalDetails(nino).value, 5.seconds).isLeft shouldBe true
      }

      "return with an error" when {
        "the call fails" in {
          inSequence {
            mockGet(connector.payePersonalDetailsUrl(nino))(None)
            // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
            mockPagerDutyAlert("Failed to make call to paye-personal-details")
          }

          Await.result(connector.getPayePersonalDetails(nino).value, 5.seconds).isLeft shouldBe true
        }

        "the call comes back with an unexpected http status" in {
          forAll { status: Int ⇒
            whenever(status > 0 && status =!= 200 && status =!= 404) {
              inSequence {
                mockGet(connector.payePersonalDetailsUrl(nino))(Some(HttpResponse(status)))
                // WARNING: do not change the message in the following check - this needs to stay in line with the configuration in alert-config
                mockPagerDutyAlert("Failed to make call to paye-personal-details")
              }

              Await.result(connector.getPayePersonalDetails(nino).value, 5.seconds).isLeft shouldBe true
            }

          }

        }

      }

    }
  }

}
