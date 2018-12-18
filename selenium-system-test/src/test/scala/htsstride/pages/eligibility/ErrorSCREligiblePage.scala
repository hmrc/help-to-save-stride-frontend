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

package htsstride.pages.eligibility

import htsstride.browser.Browser
import htsstride.pages.eligibility.CustomerEligiblePage.{clickCancel, clickSubmit, navigate}
import htsstride.pages.{ApplicationCancelledPage, CreateAccountPage, Page}
import htsstride.utils.{Configuration, CustomerDetails}
import org.openqa.selenium.WebDriver

object ErrorSCREligiblePage extends Page {

  override val expectedURL = s"${Configuration.host}/help-to-save/hmrc-internal/customer-eligible"

  override val expectedPageTitle: Option[String] = Some("Error: Customer is eligible for an account")
  override val expectedPageHeader: Option[String] = Some("Customer is eligible for an account")

  def findErrorMessageList()(implicit driver: WebDriver): Option[String] = {
    Browser.find(Browser.className("error-summary-list")).map(_.underlying.getText)
  }
}
