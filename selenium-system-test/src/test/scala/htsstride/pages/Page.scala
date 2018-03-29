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

package htsstride.pages

import htsstride.browser.Browser
import org.openqa.selenium.{By, WebDriver}

trait Page {

  val expectedURL: String

  val expectedPageTitle: Option[String] = None

  val expectedPageHeader: Option[String] = None

  def navigate()(implicit driver: WebDriver): Unit = Browser.go to expectedURL

  def clickSubmit()(implicit driver: WebDriver): Unit =
    Browser.find(Browser.className("button")).foreach(_.underlying.click())

  def clickCancel()(implicit driver: WebDriver): Unit =
    driver.findElement(By.linkText("Cancel application / End call")).click()

  def clickEndCall()(implicit driver: WebDriver): Unit =
    driver.findElement(By.linkText("End call")).click()

  def setFieldByName(name: String, value: String)(implicit driver: WebDriver): Unit =
    Browser.find(Browser.name(name)).foreach(_.underlying.sendKeys(value))

}
