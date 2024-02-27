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

package uk.gov.hmrc.helptosavestridefrontend.config

import play.api.{Configuration, Environment}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration

@Singleton
class FrontendAppConfig @Inject()(
  configuration: Configuration,
  servicesConfig: ServicesConfig,
  val environment: Environment) {
  lazy val config: Configuration = configuration

  val appName: String = servicesConfig.getString("appName")

  val mongoSessionExpireAfter: Duration = servicesConfig.getDuration("mongodb.session.expireAfter")

  object NsiBankTransferDetails {
    val sortCode: String = servicesConfig.getString("nsi-bank-transfer-details.sortcode")
    val accountNumber: String = servicesConfig.getString("nsi-bank-transfer-details.accountNumber")
  }

  object FormValidation {
    val forenameMaxTotalLength: Int = servicesConfig.getInt("applicant-details.forename.max-length")

    val addressLineMaxTotalLength: Int = servicesConfig.getInt("applicant-details.address-lines.max-length")

    val postcodeMaxTotalLength: Int = servicesConfig.getInt("applicant-details.postcode.max-length")
  }
}
