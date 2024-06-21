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

import org.scalamock.handlers.CallHandler5
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.Writes
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

trait HttpSupport { this: MockFactory with Matchers =>

  val mockHttp: HttpClient = mock[HttpClient]

  private val emptyMap = Map.empty[String, String]

  // for the query params, to check that a query parameter for a key is defined but to not check it's actual value enter
  // the value "*" for the key in the `queryParms` input
  def mockGet(url: String, queryParams: Map[String, String] = emptyMap, headers: Map[String, String] = emptyMap)(
    response: Option[HttpResponse]
  ) =
    (mockHttp
      .GET(_: String, _: Seq[(String, String)], _: Seq[(String, String)])(
        _: HttpReads[HttpResponse],
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .expects(where {
        (
          u: String,
          q: Seq[(String, String)],
          _: Seq[(String, String)],
          _: HttpReads[HttpResponse],
          h: HeaderCarrier,
          _: ExecutionContext
        ) =>
          val (ignoreQueryParams, checkQueryParams) = queryParams.partition(_._2 === "*")

          // use matchers here to get useful error messages when the following predicates
          // are not satisfied - otherwise it is difficult to tell in the logs what went wrong
          u shouldBe url
          ignoreQueryParams.keys.foreach(k =>
            withClue(s"For query parameter $k: ")(q.exists(_._1 === k) shouldBe true)
          )
          q.filterNot { case (key, _) => ignoreQueryParams.isDefinedAt(key) } shouldBe checkQueryParams.toSeq
          h.extraHeaders shouldBe headers.toSeq
          true
      })
      .returning(response.fold(Future.failed[HttpResponse](new Exception("Test exception message")))(Future.successful))

  def mockPut[A](url: String, body: A, headers: Map[String, String] = Map.empty[String, String])(
    result: Option[HttpResponse]
  ): Unit =
    (mockHttp
      .PUT(_: String, _: A, _: Seq[(String, String)])(
        _: Writes[A],
        _: HttpReads[HttpResponse],
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .expects(where {
        (
          u: String,
          a: A,
          _: Seq[(String, String)],
          _: Writes[A],
          _: HttpReads[HttpResponse],
          h: HeaderCarrier,
          _: ExecutionContext
        ) =>
          u shouldBe url
          a shouldBe body
          h.extraHeaders shouldBe headers.toSeq
          true
      })
      .returning(
        result.fold[Future[HttpResponse]](Future.failed(new Exception("Test exception message")))(Future.successful)
      )

  def mockPost[A](url: String, headers: Map[String, String], body: A)(result: Option[HttpResponse]): Unit =
    (mockHttp
      .POST(_: String, _: A, _: Seq[(String, String)])(
        _: Writes[A],
        _: HttpReads[HttpResponse],
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .expects(url, body, headers.toSeq, *, *, *, *)
      .returning(
        result.fold[Future[HttpResponse]](Future.failed(new Exception("Test exception message")))(Future.successful)
      )

  def mockDelete[O](url: String)(
    response: Option[O]
  ): CallHandler5[String, Seq[(String, String)], HttpReads[O], HeaderCarrier, ExecutionContext, Future[O]] =
    (mockHttp
      .DELETE[O](_: String, _: Seq[(String, String)])(_: HttpReads[O], _: HeaderCarrier, _: ExecutionContext))
      .expects(url, *, *, *, *)
      .returning(response.fold(Future.failed[O](new Exception("")))(Future.successful))

}
