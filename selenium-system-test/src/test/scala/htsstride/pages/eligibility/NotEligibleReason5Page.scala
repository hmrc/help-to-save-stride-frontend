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

package htsstride.pages.eligibility

object NotEligibleReason5Page extends NotEligiblePage {

  override val notEligibleText =
    List("Customer is not eligible for a Help to Save account.",
      "They are claiming Universal Credit, but their household earnings in their last monthly assessment period were too low.",
      "Their Universal Credit payments are not considered to be income.")
}
