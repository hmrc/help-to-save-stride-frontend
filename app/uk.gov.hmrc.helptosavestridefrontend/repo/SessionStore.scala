/*
 * Copyright 2021 HM Revenue & Customs
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

import cats.data.{EitherT, OptionT}
import cats.instances.either._
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Json, Reads, Writes}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.cache.model.Id
import uk.gov.hmrc.cache.repository.CacheMongoRepository
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.metrics.HTSMetrics
import uk.gov.hmrc.helptosavestridefrontend.models.HtsSession
import uk.gov.hmrc.helptosavestridefrontend.util.{PagerDutyAlerting, Result, toFuture}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[SessionStoreImpl])
trait SessionStore {

  def get(implicit reads: Reads[HtsSession], hc: HeaderCarrier): Result[Option[HtsSession]]

  def store(body: HtsSession)(implicit writes: Writes[HtsSession], hc: HeaderCarrier): Result[Unit]

  def delete(implicit hc: HeaderCarrier): Result[Unit]
}

@Singleton
class SessionStoreImpl @Inject() (mongo:             ReactiveMongoComponent,
                                  metrics:           HTSMetrics,
                                  pagerDutyAlerting: PagerDutyAlerting)(implicit appConfig: FrontendAppConfig, ec: ExecutionContext)
  extends SessionStore {

  private val expireAfterSeconds = appConfig.mongoSessionExpireAfter.toSeconds

  private val cacheRepository = new CacheMongoRepository("sessions", expireAfterSeconds)(mongo.mongoConnector.db, ec)

  private type EitherStringOr[A] = Either[String, A]

  override def get(implicit reads: Reads[HtsSession], hc: HeaderCarrier): Result[Option[HtsSession]] = {

    EitherT(hc.sessionId.map(_.value) match {
      case Some(sessionId) ⇒

        val timerContext = metrics.sessionStoreReadTimer.time()

        cacheRepository.findById(Id(sessionId)).map { maybeCache ⇒
          val response: OptionT[EitherStringOr, HtsSession] = for {
            cache ← OptionT.fromOption[EitherStringOr](maybeCache)
            data ← OptionT.fromOption[EitherStringOr](cache.data)
            result ← OptionT.liftF[EitherStringOr, HtsSession](
              (data \ "htsSession").validate[HtsSession].asEither.leftMap(e ⇒ s"Could not parse session data from mongo: ${e.mkString("; ")}"))
          } yield result

          val _ = timerContext.stop()

          response.value

        }.recover {
          case e ⇒
            val _ = timerContext.stop()
            metrics.sessionStoreReadErrorCounter.inc()
            pagerDutyAlerting.alert("unexpected error when reading stride HtsSession from mongo")
            Left(e.getMessage)
        }

      case None ⇒
        Left("can't query mongo dueto no sessionId in the HeaderCarrier")
    })
  }

  override def store(newSession: HtsSession)(implicit writes: Writes[HtsSession], hc: HeaderCarrier): Result[Unit] = {
    EitherT(hc.sessionId.map(_.value) match {
      case Some(sessionId) ⇒
        val timerContext = metrics.sessionStoreWriteTimer.time()
        cacheRepository.createOrUpdate(Id(sessionId), "htsSession", Json.toJson(newSession))
          .map[Either[String, Unit]] {
            dbUpdate ⇒
              if (dbUpdate.writeResult.inError) {
                Left(dbUpdate.writeResult.errmsg.getOrElse("unknown error during inserting session data in mongo"))
              } else {
                val _ = timerContext.stop()
                Right(())
              }
          }.recover {
            case e ⇒
              val _ = timerContext.stop()
              metrics.sessionStoreWriteErrorCounter.inc()
              pagerDutyAlerting.alert("unexpected error when writing stride HtsSession to mongo")
              Left(e.getMessage)
          }

      case None ⇒
        Left("can't store HTSSession in mongo dueto no sessionId in the HeaderCarrier")
    }
    )
  }

  def delete(implicit hc: HeaderCarrier): Result[Unit] = {
    EitherT(hc.sessionId.map(_.value) match {
      case Some(sessionId) ⇒
        val timerContext = metrics.sessionStoreDeleteTimer.time()
        cacheRepository.removeById(Id(sessionId))
          .map[Either[String, Unit]] {
            dbRemove ⇒
              if (dbRemove.writeErrors.nonEmpty) {
                Left(dbRemove.writeErrors.map(_.errmsg).mkString(", "))
              } else {
                val _ = timerContext.stop()
                Right(())
              }
          }.recover {
            case e ⇒
              val _ = timerContext.stop()
              metrics.sessionStoreDeleteErrorCounter.inc()
              pagerDutyAlerting.alert("unexpected error when deleting stride HtsSession from mongo")
              Left(e.getMessage)
          }

      case None ⇒
        Left("can't delete HTSSession in mongo dueto no sessionId in the HeaderCarrier")
    }
    )
  }
}
