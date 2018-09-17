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
import play.api.http.Status
import play.api.http.Status.OK
import play.api.{Configuration, Environment}
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.http.HttpClient
import uk.gov.hmrc.helptosavestridefrontend.metrics.Metrics
import uk.gov.hmrc.helptosavestridefrontend.metrics.Metrics.nanosToPrettyString
import uk.gov.hmrc.helptosavestridefrontend.models.CreateAccountResult.{AccountAlreadyExists, AccountCreated}
import uk.gov.hmrc.helptosavestridefrontend.models.eligibility.{EligibilityCheckResponse, EligibilityCheckResult}
import uk.gov.hmrc.helptosavestridefrontend.models.register.CreateAccountRequest
import uk.gov.hmrc.helptosavestridefrontend.models.{CreateAccountResult, EnrolmentStatus, NSIPayload, PayePersonalDetails}
import uk.gov.hmrc.helptosavestridefrontend.util.HttpResponseOps._
import uk.gov.hmrc.helptosavestridefrontend.util.Logging._
import uk.gov.hmrc.helptosavestridefrontend.util.{Logging, NINO, NINOLogMessageTransformer, PagerDutyAlerting, Result, maskNino}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[HelpToSaveConnectorImpl])
trait HelpToSaveConnector {

  def getEligibility(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResult]

  def getNSIUserInfo(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[NSIPayload]

  def createAccount(createAccountRequest: CreateAccountRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[CreateAccountResult]

  def getEnrolmentStatus(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EnrolmentStatus]

}

@Singleton
class HelpToSaveConnectorImpl @Inject() (http:                              HttpClient,
                                         metrics:                           Metrics,
                                         pagerDutyAlerting:                 PagerDutyAlerting,
                                         override val runModeConfiguration: Configuration,
                                         override val environment:          Environment)(implicit transformer: NINOLogMessageTransformer)
  extends FrontendAppConfig(runModeConfiguration, environment) with HelpToSaveConnector with Logging {

  private val htsUrl = baseUrl("help-to-save")

  private val eligibilityUrl: String = s"$htsUrl/help-to-save/stride/eligibility-check"

  private val payePersonalDetailsUrl: String = s"$htsUrl/help-to-save/stride/paye-personal-details"

  private val createAccountUrl: String = s"$htsUrl/help-to-save/create-account"

  private val enrolmentStatusUrl: String = s"$htsUrl/help-to-save/stride/enrolment-status"

  type EitherStringOr[A] = Either[String, A]

  override def getEligibility(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EligibilityCheckResult] = {
    EitherT[Future, String, EligibilityCheckResult](
      {
        val timerContext = metrics.eligibilityCheckTimer.time()

        http.get(eligibilityUrl, Map("nino" -> nino)).map { response ⇒
          val time = timerContext.stop()

          response.status match {
            case OK ⇒
              val result = {
                response.parseJson[EligibilityCheckResponse].flatMap(toEligibilityCheckResult)
              }
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

  // scalastyle:off magic.number
  private def toEligibilityCheckResult(r: EligibilityCheckResponse): Either[String, EligibilityCheckResult] =
    r.resultCode match {
      case 1     ⇒ Right(EligibilityCheckResult.Eligible(r))
      case 2     ⇒ Right(EligibilityCheckResult.Ineligible(r))
      case 3     ⇒ Right(EligibilityCheckResult.AlreadyHasAccount(r))
      case other ⇒ Left(s"Could not parse eligibility result code '$other'. Response was '$r'")
    }

  private def timeString(nanos: Long): String = s"(round-trip time: ${nanosToPrettyString(nanos)})"

  override def getNSIUserInfo(nino: NINO)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[NSIPayload] =
    EitherT[Future, String, NSIPayload](
      {
        val timerContext = metrics.payePersonalDetailsTimer.time()

        http.get(payePersonalDetailsUrl, Map("nino" -> nino))
          .map { response ⇒
            val time = timerContext.stop()
            response.status match {
              case OK ⇒
                val result = response.parseJson[PayePersonalDetails].map(_.convertToNSIUserInfo(nino))
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

  override def createAccount(createAccountRequest: CreateAccountRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[CreateAccountResult] = {

    val nSIUserInfo = createAccountRequest.payload

    EitherT[Future, String, CreateAccountResult](http.post(createAccountUrl, createAccountRequest).map[Either[String, CreateAccountResult]] { response ⇒
      response.status match {
        case Status.CREATED ⇒
          logger.debug("createAccount returned 201 (Created)", nSIUserInfo.nino)
          Right(AccountCreated)
        case Status.CONFLICT ⇒
          logger.warn(s"createAccount returned 409 (Conflict)", nSIUserInfo.nino)
          Right(AccountAlreadyExists)
        case _ ⇒
          logger.warn(s"createAccount returned a status: ${response.status} " +
            s"with response body: ${maskNino(response.body)}", nSIUserInfo.nino)
          pagerDutyAlerting.alert("Received unexpected http status from the back end when calling the create account url")
          Left(s"createAccount returned a status other than 201, and 409, status was: ${response.status} " +
            s"with response body: ${maskNino(response.body)}")
      }
    }.recover {
      case e ⇒
        logger.warn(s"Encountered error while trying to make createAccount call, with message: ${e.getMessage}", nSIUserInfo.nino)
        pagerDutyAlerting.alert("Failed to make call to the back end create account url")
        Left(s"Encountered error while trying to make createAccount call, with message: ${e.getMessage}")
    })
  }

  override def getEnrolmentStatus(nino: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Result[EnrolmentStatus] = {

    EitherT[Future, String, EnrolmentStatus](http.get(enrolmentStatusUrl, Map("nino" -> nino)).map[Either[String, EnrolmentStatus]] { response ⇒
      response.status match {
        case OK ⇒
          val result = response.parseJson[EnrolmentStatus]
          result.fold({
            e ⇒
              logger.warn(s"could not parse JSON response from enrolment status, received 200 (OK): $e", nino)
          }, _ ⇒
            logger.debug(s"Call to get enrolment status successful, received 200 (OK)", nino)
          )
          result

        case other: Int ⇒
          logger.warn(s"Call to get enrolment status unsuccessful. Received unexpected status $other", nino)
          pagerDutyAlerting.alert("Received unexpected http status from the back end when calling the get enrolment status url")
          Left(s"Received unexpected status $other")
      }
    }.recover {
      case e ⇒
        logger.warn(s"Encountered error while trying to getEnrolmentStatus, with message: ${e.getMessage}", nino)
        pagerDutyAlerting.alert("Failed to make call to the back end get enrolment status url")
        Left(s"Encountered error while trying to getEnrolmentStatus, with message: ${e.getMessage}")
    })

  }

}

