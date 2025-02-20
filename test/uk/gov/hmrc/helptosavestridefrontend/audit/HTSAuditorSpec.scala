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

package uk.gov.hmrc.helptosavestridefrontend.audit

import org.mockito.ArgumentMatchersSugar.*
import play.api.libs.json.Json
import uk.gov.hmrc.helptosavestridefrontend.TestSupport
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class HTSAuditorSpec extends TestSupport {
  val mockAuditConnector: AuditConnector = mock[AuditConnector]

  val auditor = new HTSAuditor(mockAuditConnector)

  "The HTSAuditor" when {
    "sending an event" must {
      "use the audit connector to send an event" in {
        val dataEvent: ExtendedDataEvent =
          ExtendedDataEvent("source", "type", "id", Map("tag" -> "value"), Json.obj("field" -> "value"), Instant.now)

        val htsEvent: HTSEvent = new HTSEvent {
          override val value = dataEvent
        }

        mockAuditConnector
          .sendExtendedEvent(dataEvent)(*, *)
          .returns(Future.failed(new Exception))

        auditor.sendEvent(htsEvent, "nino")
      }
    }
  }
}
