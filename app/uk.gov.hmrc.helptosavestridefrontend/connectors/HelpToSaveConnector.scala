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
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status.OK
import play.api.libs.json._
import play.api.{Configuration, Environment}
import uk.gov.hmrc.helptosavestridefrontend.config.{FrontendAppConfig, WSHttp}
import uk.gov.hmrc.helptosavestridefrontend.connectors.HelpToSaveConnector.{ECResponseHolder, PayeDetailsHolder}
import uk.gov.hmrc.helptosavestridefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavestridefrontend.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosavestridefrontend.models.PayePersonalDetails
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavestridefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavestridefrontend.util.Logging._
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, NINO, NINOLogMessageTransformer, PagerDutyAlerting, Result}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def getEligibility(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResult]

  def getPayePersonalDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[PayeDetailsHolder]

}

@Singleton
class HelpToSaveConnectorImpl @Inject() (http:                              WSHttp,
                                         metrics:                           Metrics,
                                         pagerDutyAlerting:                 PagerDutyAlerting,
                                         override val runModeConfiguration: Configuration,
                                         override val environment:          Environment)(implicit transformer: NINOLogMessageTransformer)
  extends FrontendAppConfig(runModeConfiguration, environment) with HelpToSaveConnector with Logging {

  private val htsUrl = baseUrl("help-to-save")

  def eligibilityUrl(nino: String): String = s"$htsUrl/help-to-save/stride/eligibility-check?nino=$nino"

  def payePersonalDetailsUrl(nino: String): String = s"$htsUrl/help-to-save/stride/paye-personal-details?nino=$nino"

  type EitherStringOr[A] = Either[String, A]

  override def getEligibility(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResult] = {
    EitherT[Future, String, EligibilityCheckResult](
      {
        val timerContext = metrics.eligibilityCheckTimer.time()

        http.get(eligibilityUrl(nino)).map { response ⇒
          val time = timerContext.stop()

          response.status match {
            case OK ⇒
              val result = response.parseJson[ECResponseHolder].flatMap(ecHolder ⇒ toEligibilityCheckResult(ecHolder.response))
              result.fold({
                e ⇒
                  metrics.eligibilityCheckErrorCounter.inc()
                  logger.warn(s"Could not parse JSON response from eligibility check, received 200 (OK): $e ${timeString(time)}", nino)
                  pagerDutyAlerting.alert("Could not parse JSON in eligibility check response")
              }, _ ⇒
                logger.info(s"Call to check eligibility successful, received 200 (OK) ${timeString(time)}", nino)
              )
              result

            case other: Int ⇒
              logger.warn(s"Call to check eligibility unsuccessful. Received unexpected status $other ${timeString(time)}", nino)
              metrics.eligibilityCheckErrorCounter.inc()
              pagerDutyAlerting.alert("Received unexpected http status in response to eligibility check")
              Left(s"Received unexpected status $other")

          }
        }.recover {
          case e ⇒
            val time = timerContext.stop()
            pagerDutyAlerting.alert("Failed to make call to check eligibility")
            metrics.eligibilityCheckErrorCounter.inc()
            Left(s"Call to check eligibility unsuccessful: ${e.getMessage} (round-trip time: ${timeString(time)})")
        }
      }
    )
  }

  private val emptyECResponse = EligibilityCheckResponse("No tax credit record found for user's NINO", 2, "", -1)

  // scalastyle:off magic.number
  private def toEligibilityCheckResult(response: Option[EligibilityCheckResponse]): Either[String, EligibilityCheckResult] =
    response.fold[Either[String, EligibilityCheckResult]](Right(EligibilityCheckResult.Ineligible(emptyECResponse))) { r ⇒
      r.resultCode match {
        case 1     ⇒ Right(EligibilityCheckResult.Eligible(r))
        case 2     ⇒ Right(EligibilityCheckResult.Ineligible(r))
        case 3     ⇒ Right(EligibilityCheckResult.AlreadyHasAccount(r))
        case other ⇒ Left(s"Could not parse eligibility result code '$other'. Response was '$r'")
      }
    }

  private def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"

  override def getPayePersonalDetails(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[PayeDetailsHolder] =
    EitherT[Future, String, PayeDetailsHolder](
      {
        val timerContext = metrics.payePersonalDetailsTimer.time()

        http.get(payePersonalDetailsUrl(nino))(hc.copy(authorization = None), ec)
          .map { response ⇒
            val time = timerContext.stop()
            response.status match {
              case OK ⇒
                val result = response.parseJson[PayeDetailsHolder]
                result.fold({
                  e ⇒
                    metrics.payePersonalDetailsErrorCounter.inc()
                    logger.warn(s"Could not parse JSON response from paye-personal-details, received 200 (OK): $e ${timeString(time)}", nino)
                    pagerDutyAlerting.alert("Could not parse JSON in the paye-personal-details response")
                }, _ ⇒
                  logger.info(s"Call to check paye-personal-details successful, received 200 (OK) ${timeString(time)}", nino)
                )
                result

              case other: Int ⇒
                logger.warn(s"Call to paye-personal-details unsuccessful. Received unexpected status $other ${timeString(time)}", nino)
                metrics.payePersonalDetailsErrorCounter.inc()
                pagerDutyAlerting.alert("Received unexpected http status in response to paye-personal-details")
                Left(s"Received unexpected status $other")
            }
          }.recover {
            case e ⇒
              val time = timerContext.stop()
              pagerDutyAlerting.alert("Failed to make call to paye-personal-details")
              metrics.payePersonalDetailsErrorCounter.inc()
              Left(s"Call to paye-personal-details unsuccessful: ${e.getMessage} (round-trip time: ${timeString(time)})")
          }
      })

}

object HelpToSaveConnector {

  private[connectors] case class ECResponseHolder(response: Option[EligibilityCheckResponse])

  private[connectors] object ECResponseHolder {

    implicit val format: Format[ECResponseHolder] = new Format[ECResponseHolder] {

      private val writesInstance = Json.writes[ECResponseHolder]

      override def writes(o: ECResponseHolder): JsValue = writesInstance.writes(o)

      // fail if there is anything other than `response` in the JSON
      override def reads(json: JsValue): JsResult[ECResponseHolder] = {
        val map = json.as[JsObject].value
        map.get("response").fold[JsResult[ECResponseHolder]] {
          if (map.keySet.nonEmpty) {
            JsError(s"Unexpected keys: ${map.keySet.mkString(",")}")
          } else {
            JsSuccess(ECResponseHolder(None))
          }
        } {
          _.validate[EligibilityCheckResponse].map(r ⇒ ECResponseHolder(Some(r)))
        }
      }
    }
  }

  case class PayeDetailsHolder(payeDetails: Option[PayePersonalDetails])

  object PayeDetailsHolder {
    implicit val format: Format[PayeDetailsHolder] = new Format[PayeDetailsHolder] {

      private val writesInstance = Json.writes[PayeDetailsHolder]

      override def writes(o: PayeDetailsHolder): JsValue = writesInstance.writes(o)

      // fail if there is anything other than `payeDetails` in the JSON
      override def reads(json: JsValue): JsResult[PayeDetailsHolder] = {
        val map = json.as[JsObject].value
        map.get("payeDetails").fold[JsResult[PayeDetailsHolder]] {
          if (map.keySet.nonEmpty) {
            JsError(s"Unexpected keys: ${map.keySet.mkString(",")}")
          } else {
            JsSuccess(PayeDetailsHolder(None))
          }
        } {
          _.validate[PayePersonalDetails].map(r ⇒ PayeDetailsHolder(Some(r)))
        }
      }
    }
  }

}
