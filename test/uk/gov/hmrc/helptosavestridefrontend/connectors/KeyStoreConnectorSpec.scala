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

import java.util.UUID

import org.scalamock.handlers.{CallHandler4, CallHandler6}
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.UserSessionInfo
import uk.gov.hmrc.helptosavestridefrontend.util.MockPagerDuty
import uk.gov.hmrc.helptosavestridefrontend.{TestData, TestSupport}
import uk.gov.hmrc.http.cache.client.CacheMap
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class KeyStoreConnectorSpec extends TestSupport with MockPagerDuty with TestData {

  val connector = new KeyStoreConnectorImpl(mockHttp, mockMetrics, mockPagerDuty, configuration, environment)

  def mockPut[I, O](url: String, body: I)(response: Option[O]): CallHandler6[String, I, Writes[I], HttpReads[O], HeaderCarrier, ExecutionContext, Future[O]] =
    (mockHttp.PUT[I, O](_: String, _: I)(_: Writes[I], _: HttpReads[O], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, body, *, *, *, *)
      .returning(response.fold(Future.failed[O](new Exception("")))(Future.successful))

  def mockGet[I](url: String)(response: Option[I]): CallHandler4[String, HttpReads[I], HeaderCarrier, ExecutionContext, Future[I]] =
    (mockHttp.GET[I](_: String)(_: HttpReads[I], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *, *)
      .returning(response.fold(Future.failed[I](new Exception("")))(Future.successful))

  "KeyStoreConnector" when {

    "storing stride-user-info" must {

      val key = UUID.randomUUID().toString

      val url = s"http://localhost:8400/keystore/help-to-save-stride-frontend/stride-user-info/data/$key"

      val response = CacheMap("someId", Map.empty)

      val body = UserSessionInfo.EligibleWithPayePersonalDetails(eligibleResponse.value, ppDetails)

      "store user-info as expected" in {

        mockPut[UserSessionInfo, CacheMap](url, body)(Some(response))

        Await.result(connector.put(key, body).value, 5.seconds) shouldBe Right(())

      }

      "handle unexpected errors" in {

        mockPut[UserSessionInfo, CacheMap](url, body)(None)
        mockPagerDutyAlert("unexpected error when storing stride-user-info to keystore")

        Await.result(connector.put(key, body).value, 5.seconds).isLeft shouldBe true

      }
    }

    "retrieving the stride-user-info" must {

      val key = UUID.randomUUID().toString

      val url = "http://localhost:8400/keystore/help-to-save-stride-frontend/stride-user-info"

      val body = UserSessionInfo.EligibleWithPayePersonalDetails(eligibleResponse.value, ppDetails)

      val response = CacheMap(key, Map(key -> Json.toJson(body)))

      "return successful result when retrieving stride-user-info" in {

        mockGet[CacheMap](url)(Some(response))

        Await.result(connector.get(key).value, 5.seconds) shouldBe Right(Some(body))

      }

      "handle unexpected errors" in {

        mockGet[CacheMap](url)(None)
        mockPagerDutyAlert("unexpected error when retrieving stride-user-info from keystore")

        Await.result(connector.get(key).value, 5.seconds).isLeft shouldBe true

      }
    }
  }
}
