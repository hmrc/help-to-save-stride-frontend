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

import org.scalamock.handlers.{CallHandler4, CallHandler6}
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.{HtsSession, EligibilityCheckResultWithInfo}
import uk.gov.hmrc.helptosavestridefrontend.util.MockPagerDuty
import uk.gov.hmrc.helptosavestridefrontend.{TestData, TestSupport}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class KeyStoreConnectorSpec extends TestSupport with MockPagerDuty with TestData { // scalastyle:off magic.number

  val connector = new KeyStoreConnectorImpl(mockHttp, mockMetrics, mockPagerDuty, configuration, environment)

  def mockPut[I, O](url: String, body: I)(response: Option[O]): CallHandler6[String, I, Writes[I], HttpReads[O], HeaderCarrier, ExecutionContext, Future[O]] =
    (mockHttp.PUT[I, O](_: String, _: I)(_: Writes[I], _: HttpReads[O], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, body, *, *, *, *)
      .returning(response.fold(Future.failed[O](new Exception("")))(Future.successful))

  def mockGet[I](url: String)(response: Option[I]): CallHandler4[String, HttpReads[I], HeaderCarrier, ExecutionContext, Future[I]] =
    (mockHttp.GET[I](_: String)(_: HttpReads[I], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *, *)
      .returning(response.fold(Future.failed[I](new Exception("")))(Future.successful))

  def mockDelete[O](url: String)(response: Option[O]): CallHandler4[String, HttpReads[O], HeaderCarrier, ExecutionContext, Future[O]] =
    (mockHttp.DELETE[O](_: String)(_: HttpReads[O], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *, *)
      .returning(response.fold(Future.failed[O](new Exception("")))(Future.successful))

  class TestApparatus {

    val sessionId = headerCarrier.sessionId.getOrElse(sys.error("Could not find session iD"))

    def cacheMap(htsSession: HtsSession) = CacheMap("1", Map("htsSession" -> Json.toJson(htsSession)))

    val body = HtsSession(EligibilityCheckResultWithInfo.EligibleWithNSIUserWithInfo(eligibleResponse.value, nsiUserInfo), nsiUserInfo)

    val response = cacheMap(body)

    val putUrl = s"http://localhost:8400/keystore/help-to-save-stride-frontend/${sessionId.value}/data/htsSession"
    val getUrl = s"http://localhost:8400/keystore/help-to-save-stride-frontend/${sessionId.value}"
    val deleteUrl = s"http://localhost:8400/keystore/help-to-save-stride-frontend/${sessionId.value}"
  }

  "KeyStoreConnector" when {

    "storing" must {

      "store user session info as expected" in new TestApparatus {

        mockPut[HtsSession, CacheMap](putUrl, body)(Some(response))

        Await.result(connector.put(body).value, 5.seconds) shouldBe Right(response)

      }

      "handle unexpected errors" in new TestApparatus {

        mockPut[HtsSession, CacheMap](putUrl, body)(None)
        mockPagerDutyAlert("unexpected error when storing UserSessionInfo to keystore")

        Await.result(connector.put(body).value, 5.seconds).isLeft shouldBe true

      }
    }

    "retrieving" must {

      "return successful result when retrieving user session info" in new TestApparatus {

        mockGet[CacheMap](getUrl)(Some(response))

        Await.result(connector.get.value, 5.seconds) shouldBe Right(Some(body))

      }

      "handle unexpected errors" in new TestApparatus {

        mockGet[CacheMap](getUrl)(None)
        mockPagerDutyAlert("unexpected error when retrieving UserSessionInfo from keystore")

        Await.result(connector.get.value, 5.seconds).isLeft shouldBe true

      }
    }

    "deleting" must {

      "return successful result after deleting user session info" in new TestApparatus {

        val res = Some(HttpResponse(204))
        mockDelete(deleteUrl)(res)

        Await.result(connector.delete.value, 5.seconds) shouldBe Right(())

      }

      "handle responses other than 204 from keystore when deleting user session info" in new TestApparatus {

        val res = Some(HttpResponse(400))
        inSequence {
          mockDelete(deleteUrl)(res)
          mockPagerDutyAlert("unexpected error when deleting UserSessionInfo from keystore")
        }
        Await.result(connector.delete.value, 5.seconds).isLeft shouldBe true

      }

      "handle unexpected errors" in new TestApparatus {

        inSequence {
          mockDelete(deleteUrl)(None)
          mockPagerDutyAlert("unexpected error when deleting UserSessionInfo from keystore")
        }

        Await.result(connector.delete.value, 5.seconds).isLeft shouldBe true

      }
    }
  }
}
