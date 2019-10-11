/*
 * Copyright 2019 HM Revenue & Customs
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

package htsstride.pages

import java.io.File

import cucumber.api.Scenario
import cucumber.api.scala.{EN, ScalaDsl}
import htsstride.browser.Browser
import htsstride.utils.Configuration
import org.apache.commons.io.FileUtils
import org.openqa.selenium.{By, OutputType, TakesScreenshot, WebDriver}
import org.scalatest.Matchers
import uk.gov.hmrc.webdriver.SingletonDriver

trait BasePage extends ScalaDsl with EN with Matchers {

  val expectedURL: String = ""

  val expectedPageTitle: Option[String] = None
  val expectedPageHeader: Option[String] = None

  implicit val driver: WebDriver = SingletonDriver.getInstance()

  def navigate()(implicit driver: WebDriver): Unit = Browser.go to expectedURL

  def clickSubmit()(implicit driver: WebDriver): Unit =
    Browser.find(Browser.className("button")).foreach(_.underlying.click())

  def clickSubmitForManualAccount()(implicit driver: WebDriver): Unit =
    Browser.find(Browser.id("continue")).foreach(_.underlying.click())

  def clickEndCall()(implicit driver: WebDriver): Unit =
    driver.findElement(By.id("end-call")).click()

  def checkConfirmEligible()(implicit driver: WebDriver): Unit =
    driver.findElement(By.id("confirm_eligible")).click()

  def setFieldByName(name: String, value: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name(name)).foreach(_.underlying.sendKeys(value))

  // To take a screeshot and embed in to the Cucumber report
  private def takeScreenshot(scenario: Scenario, s: String, dr: WebDriver with TakesScreenshot): Unit = {
    val name = scenario.getName

    if (!new java.io.File(s"./target/screenshots/$name$s.png").exists) {
      dr.manage().window().maximize()
      val scr = dr.getScreenshotAs(OutputType.FILE)
      FileUtils.copyFile(scr, new File(s"./target/screenshots/$name$s.png"))
      val byteFile = dr.getScreenshotAs(OutputType.BYTES)
      scenario.embed(byteFile, "image/png")
    }
  }

  Before { _ ⇒
    if (Configuration.scenarioLoop) {
      driver.manage().deleteAllCookies()
      //      driver.get(s"${Configuration.authHost}/auth-login-stub/session/logout")
      Configuration.scenarioLoop = false
    }
  }

  After { scenario ⇒
    if (!Configuration.scenarioLoop) {
      Configuration.scenarioLoop = true
      if (scenario.isFailed) {
        driver match {
          case a: TakesScreenshot ⇒
            takeScreenshot(scenario, "-page-on-failure", a)
            println(s"Page of failure was: ${driver.getCurrentUrl}")
            a.navigate().back()
            takeScreenshot(scenario, "-previous-page", a)
            println(s"Previous page was: ${driver.getCurrentUrl}")
        }
      }
    }
  }

}
