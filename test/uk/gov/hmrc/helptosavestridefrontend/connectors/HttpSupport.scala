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

import org.scalamock.handlers.CallHandler3
import org.scalamock.scalatest.MockFactory
import org.scalatest.Matchers
import play.api.libs.json.Writes
import play.api.libs.ws.WSClient
import uk.gov.hmrc.helptosavestridefrontend.TestSupport
import uk.gov.hmrc.helptosavestridefrontend.http.HttpClient
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.{ExecutionContext, Future}

trait HttpSupport {
  this: MockFactory with TestSupport with Matchers ⇒

  private val emptyMap = Map.empty[String, String]

  private val mockAuditor = mock[AuditConnector]
  private val mockWsClient = mock[WSClient]

  class FakedHttpClient extends HttpClient(mockAuditor, configuration, mockWsClient)

  val mockHttp: HttpClient = mock[FakedHttpClient]

  def mockGet(url: String, queryParams: Map[String, String] = emptyMap, headers: Map[String, String] = emptyMap)(response: Option[HttpResponse]) =
    (mockHttp.get(_: String, _: Map[String, String], _: Map[String, String])(_: HeaderCarrier, _: ExecutionContext))
      .expects(where { (u: String, q: Map[String, String], h: Map[String, String], hc: HeaderCarrier, _: ExecutionContext) ⇒
        // use matchers here to get useful error messages when the following predicates
        // are not satisfied - otherwise it is difficult to tell in the logs what went wrong
        u shouldBe url
        q shouldBe queryParams
        hc.extraHeaders shouldBe h.toSeq
        true
      })
      .returning(response.fold(Future.failed[HttpResponse](new Exception("Test exception message")))(Future.successful))

  def mockPut[A](url: String, body: A, headers: Map[String, String] = Map.empty[String, String])(result: Option[HttpResponse]): Unit =
    (mockHttp.put(_: String, _: A, _: Map[String, String])(_: Writes[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(where { (u: String, a: A, h: Map[String, String], _: Writes[A], hc: HeaderCarrier, _: ExecutionContext) ⇒
        u shouldBe url
        a shouldBe body
        hc.extraHeaders shouldBe headers.toSeq
        true
      })
      .returning(result.fold[Future[HttpResponse]](Future.failed(new Exception("Test exception message")))(Future.successful))

  def mockPost[A](url: String, headers: Map[String, String], body: A)(result: Option[HttpResponse]): Unit =
    (mockHttp.post(_: String, _: A, _: Map[String, String])(_: Writes[A], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, body, headers, *, *, *)
      .returning(result.fold[Future[HttpResponse]](Future.failed(new Exception("Test exception message")))(Future.successful))

  def mockDelete(url: String)(response: Option[HttpResponse]): CallHandler3[String, HeaderCarrier, ExecutionContext, Future[HttpResponse]] =
    (mockHttp.delete(_: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *)
      .returning(response.fold(Future.failed[HttpResponse](new Exception("")))(Future.successful))

}
