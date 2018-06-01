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

object NotEligibleReason4And9Page extends NotEligiblePage {

  override val notEligibleText =
    List("Customer is not eligible for a Help to Save account.",
      "To be eligible for an account one of these must apply. The customer must be:",
      "entitled to Working Tax Credit and also receiving payments for Working Tax Credit or Child Tax Credit",
      "claiming Universal Credit and have met the earnings criteria in their last monthly assessment period",
      "Payments from Universal Credit are not considered to be income.",
      "If they have recently applied for Working Tax Credit or Universal Credit they may be eligible for an account, but will need to wait and apply again later.")
}
