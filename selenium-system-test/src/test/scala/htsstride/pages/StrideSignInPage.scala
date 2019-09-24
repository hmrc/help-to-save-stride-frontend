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

import htsstride.browser.Browser
import htsstride.utils.Configuration
import org.openqa.selenium.WebDriver

object StrideSignInPage extends BasePage {

  val successURL: String =
    if (Configuration.redirectWithAbsoluteUrls) {
      "http://localhost:7006/help-to-save/hmrc-internal/check-eligibility"
    } else {
      "/help-to-save/hmrc-internal/check-eligibility"
    }

  override val expectedURL = s"${Configuration.strideAuthFrontendHost}/stride/sign-in?successURL=$successURL&origin=help-to-save-stride-frontend"

  def authenticateOperator(role: String = "hts_helpdesk_advisor")(implicit driver: WebDriver): Unit = {
    navigate()
    fillInStrideDetails(role)
    clickSubmit()
    Browser.checkCurrentPageIs(CheckEligibilityPage)
  }

  def authenticateSCROperator(role: String = "hts_helpdesk_advisor_secure")(implicit driver: WebDriver): Unit = {
    navigate()
    fillInStrideDetails(role)
    clickSubmit()
    Browser.checkCurrentPageIs(CheckEligibilityPage)
  }

  private def fillInStrideDetails(role: String)(implicit driver: WebDriver): Unit = {
    setFieldByName("pid", "pid1234")
    setFieldByName("usersGivenName", "test-given-name")
    setFieldByName("usersSurname", "test-surname")
    setFieldByName("emailAddress", "test@hmrc-hts.com")
    setFieldByName("status", "true")
    setFieldByName("signature", "valid")
    setFieldByName("roles", role)
  }

}
