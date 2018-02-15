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
import play.api.http.Status
import play.api.libs.json.{Reads, Writes}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.helptosavestridefrontend.config.{FrontendAppConfig, WSHttp}
import uk.gov.hmrc.helptosavestridefrontend.controllers.SessionBehaviour.HtsSession
import uk.gov.hmrc.helptosavestridefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, PagerDutyAlerting, Result}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.{CacheMap, SessionCache}
import uk.gov.hmrc.play.config.AppName

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[KeyStoreConnectorImpl])
trait KeyStoreConnector {

  def put(body: HtsSession)(implicit writes: Writes[HtsSession], hc: HeaderCarrier, ec: ExecutionContext): Result[CacheMap]

  def get(implicit reads: Reads[HtsSession], hc: HeaderCarrier, ec: ExecutionContext): Result[Option[HtsSession]]

  def delete(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit]

}

@Singleton
class KeyStoreConnectorImpl @Inject() (val http:                          WSHttp,
                                       metrics:                           Metrics,
                                       pagerDutyAlerting:                 PagerDutyAlerting,
                                       override val runModeConfiguration: Configuration,
                                       override val environment:          Environment
)
  extends FrontendAppConfig(runModeConfiguration, environment) with KeyStoreConnector with SessionCache with Logging with AppName {

  override protected def appNameConfiguration: Configuration = runModeConfiguration

  def defaultSource: String = appName

  def baseUri: String = baseUrl("keystore")

  def domain: String = "keystore"

  val sessionKey: String = "htsSession"

  override def put(body: HtsSession)(implicit writes: Writes[HtsSession],
                                     hc: HeaderCarrier,
                                     ec: ExecutionContext): Result[CacheMap] =
    EitherT[Future, String, CacheMap] {
      val timerContext = metrics.keystoreWriteTimer.time()

      cache[HtsSession](sessionKey, body).map { cacheMap ⇒
        val _ = timerContext.stop()
        Right(cacheMap)
      }.recover {
        case NonFatal(e) ⇒
          val _ = timerContext.stop()
          metrics.keystoreWriteErrorCounter.inc()
          logger.warn(s"unexpected error when writing UserSessionInfo to keystore, error=${e.getMessage}")
          pagerDutyAlerting.alert("unexpected error when storing UserSessionInfo to keystore")
          Left(e.getMessage)
      }
    }

  override def get(implicit reads: Reads[HtsSession],
                   hc: HeaderCarrier,
                   ec: ExecutionContext): Result[Option[HtsSession]] =
    EitherT[Future, String, Option[HtsSession]] {
      val timerContext = metrics.keystoreReadTimer.time()

      fetchAndGetEntry[HtsSession](sessionKey).map { session ⇒
        val _ = timerContext.stop()
        Right(session)
      }.recover {
        case NonFatal(e) ⇒
          val _ = timerContext.stop()
          metrics.keystoreReadErrorCounter.inc()
          logger.warn(s"unexpected error when retrieving UserSessionInfo from keystore, error=${e.getMessage}")
          pagerDutyAlerting.alert("unexpected error when retrieving UserSessionInfo from keystore")
          Left(e.getMessage)
      }
    }

  override def delete(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[Unit] =
    EitherT[Future, String, Unit] {
      val timerContext = metrics.keystoreDeleteTimer.time()

      remove().map[Either[String, Unit]] { response ⇒
        response.status match {
          case Status.NO_CONTENT ⇒
            val _ = timerContext.stop()
            Right(())
          case other: Int ⇒
            metrics.keystoreDeleteErrorCounter.inc()
            pagerDutyAlerting.alert("unexpected error when deleting UserSessionInfo from keystore")
            Left(s"unexpected error when deleting UserSessionInfo from keystore, status=$other")
        }
      }.recover {
        case NonFatal(e) ⇒
          val _ = timerContext.stop()
          metrics.keystoreDeleteErrorCounter.inc()
          pagerDutyAlerting.alert("unexpected error when deleting UserSessionInfo from keystore")
          Left(e.getMessage)
      }
    }
}
