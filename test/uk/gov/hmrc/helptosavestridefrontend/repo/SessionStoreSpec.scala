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

package uk.gov.hmrc.helptosavestridefrontend.repo

import org.mockito.IdiomaticMockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.helptosavestridefrontend.models.HtsSession._
import uk.gov.hmrc.helptosavestridefrontend.models.{HtsSecureSession, HtsStandardSession, SessionEligibilityCheckResult}
import uk.gov.hmrc.helptosavestridefrontend.util.{MockPagerDuty, WireMockMethods}
import uk.gov.hmrc.helptosavestridefrontend.{TestData, TestSupport}
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.test.MongoSupport

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
class SessionStoreSpec
    extends TestSupport with MongoSupport with IdiomaticMockito with MockPagerDuty with TestData with ScalaFutures
    with WireMockSupport with WireMockMethods {

  val timeStampSupport = new CurrentTimestampSupport()
  val sessionStore =
    new SessionStoreImpl(mongoComponent, mockMetrics, timeStampSupport = timeStampSupport, mockPagerDuty)

  implicit override val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(200, Millis)), interval = scaled(Span(5, Millis)))

  "SessionStore" when {

    val htsSession = HtsStandardSession(SessionEligibilityCheckResult.Eligible(eligibleResponse.value), nsiUserInfo)

    val htsSecureSession =
      HtsSecureSession("AE123456C", SessionEligibilityCheckResult.Eligible(eligibleResponse.value), Some(nsiUserInfo))

    "be able to insert a HtsStandardSession into and read from mongo" in {

      val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(UUID.randomUUID().toString)))
      val result = sessionStore.store(htsSession)(format, hc)

      await(result.value) should be(Right(()))

      val getResult = sessionStore.get(format, hc)
      await(getResult.value) should be(Right(Some(htsSession)))
    }

    "be able to insert a HtsSecureSession into and read from mongo" in {

      val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(UUID.randomUUID().toString)))
      val result = sessionStore.store(htsSecureSession)(format, hc)

      result.value.futureValue should be(Right(()))

      val getResult = sessionStore.get(format, hc)
      getResult.value.futureValue should be(Right(Some(htsSecureSession)))
    }

    "be able to delete a HTSSession from mongo" in {

      val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(UUID.randomUUID().toString)))
      val result = sessionStore.store(htsSession)(format, hc)

      result.value.futureValue should be(Right(()))

      val deleteResult = sessionStore.delete(hc)
      deleteResult.value.futureValue should be(Right(()))
    }
  }
}
