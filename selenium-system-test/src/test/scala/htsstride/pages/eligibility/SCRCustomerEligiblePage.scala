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
import htsstride.pages.{ApplicationCancelledPage, CreateAccountPage, Page}
import htsstride.utils.{Configuration, CustomerDetails}
import org.openqa.selenium.WebDriver

object SCRCustomerEligiblePage extends Page {
  override val expectedURL = s"${Configuration.host}/help-to-save/hmrc-internal/customer-eligible"

  override val expectedPageTitle: Option[String] = Some("Customer is eligible for an account")
  override val expectedPageHeader: Option[String] = Some("Customer is eligible for an account")

  def continue()(implicit driver: WebDriver): Unit = {
    navigate()
    clickSubmit()
    Browser.checkCurrentPageIs(CreateAccountPage)
  }

  def fillInSCRDetails(customerDetails: CustomerDetails)(implicit driver: WebDriver): Unit = {
    customerDetails.firstName.foreach(setFieldByName("forename", _))
    customerDetails.lastName.foreach(setFieldByName("surname", _))
    customerDetails.dobDay.foreach(setFieldByName("dob-day", _))
    customerDetails.dobMonth.foreach(setFieldByName("dob-month", _))
    customerDetails.dobYear.foreach(setFieldByName("dob-year", _))
    customerDetails.addressLine1.foreach(setFieldByName("address1", _))
    customerDetails.addressLine2.foreach(setFieldByName("address2", _))
    customerDetails.addressLine3.foreach(setFieldByName("address3", _))
    customerDetails.addressLine4.foreach(setFieldByName("address4", _))
    customerDetails.addressLine5.foreach(setFieldByName("address5", _))
    customerDetails.postcode.foreach(setFieldByName("postcode", _))
    clickSubmit()
  }
}
