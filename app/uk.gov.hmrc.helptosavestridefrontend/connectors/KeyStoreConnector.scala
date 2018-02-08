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

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Reads, Writes}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.helptosavestridefrontend.config.{FrontendAppConfig, WSHttp}
import uk.gov.hmrc.helptosavestridefrontend.controllers.UserInfo
import uk.gov.hmrc.helptosavestridefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavestridefrontend.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, PagerDutyAlerting, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.HttpCaching

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[KeyStoreConnectorImpl])
trait KeyStoreConnector {

  def put(key: String, body: UserInfo)(implicit writes: Writes[UserInfo], hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

  def get(key: String)(implicit reads: Reads[UserInfo], hc: HeaderCarrier, ec: ExecutionContext): Result[Option[UserInfo]]

}

@Singleton
class KeyStoreConnectorImpl @Inject() (val http:                          WSHttp,
                                       metrics:                           Metrics,
                                       pagerDutyAlerting:                 PagerDutyAlerting,
                                       override val runModeConfiguration: Configuration,
                                       override val environment:          Environment
)
  extends FrontendAppConfig(runModeConfiguration, environment) with KeyStoreConnector with HttpCaching with Logging {

  def defaultSource: String = "help-to-save-stride-frontend"

  def baseUri: String = baseUrl("keystore")

  def domain: String = "keystore"

  private val formId = "stride-user-info"

  override def put(key: String, body: UserInfo)(implicit writes: Writes[UserInfo],
                                                hc: HeaderCarrier,
                                                ec: ExecutionContext): Result[Unit] = {
    EitherT[Future, String, Unit](
      {
        val timerContext = metrics.keystoreWriteTimer.time()
        cache(defaultSource, formId, key, body)
          .map { _ ⇒
            val time = timerContext.stop()
            Right(())
          }
          .recover {
            case e ⇒
              val time = timerContext.stop()
              metrics.keystoreWriteErrorCounter.inc()
              logger.warn(s"unexpected error when writing stride-user-info to keystore, error=${e.getMessage}")
              pagerDutyAlerting.alert("unexpected error when storing stride-user-info to keystore")
              Left(s"error when storing stride-user-info to keystore, id=$key, error=${e.getMessage}, ${timeString(time)}")
          }
      })
  }

  override def get(key: String)(implicit reads: Reads[UserInfo],
                                hc: HeaderCarrier,
                                ec: ExecutionContext): Result[Option[UserInfo]] = {
    EitherT[Future, String, Option[UserInfo]](
      {
        val timerContext = metrics.keystoreReadTimer.time()
        fetchAndGetEntry[UserInfo](defaultSource, formId, key)
          .map { details ⇒
            val time = timerContext.stop()
            Right(details)
          }
          .recover {
            case e ⇒
              val time = timerContext.stop()
              metrics.keystoreReadErrorCounter.inc()
              logger.warn(s"unexpected error when retrieving stride-user-info from keystore, error=${e.getMessage}")
              pagerDutyAlerting.alert("unexpected error when retrieving stride-user-info from keystore")
              Left(s"error when retrieving stride-user-info from keystore, id=$key, error=${e.getMessage},(round-trip time: ${timeString(time)})")
          }
      })
  }

  private def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"

}
