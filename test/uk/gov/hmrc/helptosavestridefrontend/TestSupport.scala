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

package uk.gov.hmrc.helptosavestridefrontend

import com.codahale.metrics._
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Suite}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.MessagesControllerComponents
import play.api.{Application, Configuration, Environment, Play}
import play.filters.csrf.CSRFAddToken
import uk.gov.hmrc.helptosavestridefrontend.config.{ErrorHandler, FrontendAppConfig}
import uk.gov.hmrc.helptosavestridefrontend.metrics.HTSMetrics
import uk.gov.hmrc.helptosavestridefrontend.util.{NINOLogMessageTransformer, WireMockMethods}
import uk.gov.hmrc.helptosavestridefrontend.views.html.error_template
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, SessionId}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util
import java.util.UUID
import scala.concurrent.ExecutionContext

trait TestSupport
    extends UnitSpec with MockFactory with BeforeAndAfterAll with ScalaFutures with I18nSupport with WireMockSupport
    with WireMockMethods {
  this: Suite =>

  lazy val additionalConfig = Configuration()

  implicit lazy val fakeApplication: Application =
    new GuiceApplicationBuilder()
      .configure(
        Configuration(
          ConfigFactory.parseString(s"""
                                       | metrics.enabled = true
                                       | metrics.jvm = false
                                       | mongodb.session.expireAfter = 5 seconds
                                       | microservice.services.help-to-save.host = $wireMockHost
                                       | microservice.services.help-to-save.port = $wireMockPort
          """.stripMargin)
        ) withFallback additionalConfig
      )
      .build()

  lazy val injector: Injector = fakeApplication.injector

  lazy implicit val environment: Environment = injector.instanceOf[Environment]

  lazy implicit val configuration: Configuration = injector.instanceOf[Configuration]

  lazy implicit val servicesConfig: ServicesConfig = injector.instanceOf[ServicesConfig]

  implicit lazy val ec: ExecutionContext = injector.instanceOf[ExecutionContext]

  implicit lazy val ninoLogMessageTransfromer: NINOLogMessageTransformer =
    injector.instanceOf[NINOLogMessageTransformer]

  implicit val headerCarrier: HeaderCarrier =
    HeaderCarrier(sessionId = Some(SessionId(UUID.randomUUID().toString)))

  val nino = "AE123456C"

  class StubMetricRegistry extends MetricRegistry {
    override def getGauges(filter: MetricFilter): util.SortedMap[String, Gauge[_]] =
      new util.TreeMap[String, Gauge[_]]()
  }

  val mockMetrics = new HTSMetrics(stub[MetricRegistry]) {
    override def timer(name: String): Timer = new Timer()

    override def counter(name: String): Counter = new Counter()
  }

  override protected def beforeAll(): Unit = {
    Play.start(fakeApplication)
    super.beforeAll()
  }

  override protected def afterAll(): Unit = {
    Play.stop(fakeApplication)
    super.afterAll()
  }

  lazy val messagesApi: MessagesApi = injector.instanceOf(classOf[MessagesApi])

  implicit lazy val frontendAppConfig: FrontendAppConfig = injector.instanceOf[FrontendAppConfig]
  lazy val errorHandler = new ErrorHandler(testMcc.messagesApi, injector.instanceOf[error_template], frontendAppConfig)

  lazy val csrfAddToken: CSRFAddToken = injector.instanceOf[play.filters.csrf.CSRFAddToken]

  lazy val testMcc: MessagesControllerComponents = injector.instanceOf[MessagesControllerComponents]
}
