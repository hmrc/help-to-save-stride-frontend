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

package htsstride.utils

case class CustomerDetails(firstName:    Option[String],
                           lastName:     Option[String],
                           dobDay:       Option[String],
                           dobMonth:     Option[String],
                           dobYear:      Option[String],
                           addressLine1: Option[String],
                           addressLine2: Option[String],
                           addressLine3: Option[String],
                           addressLine4: Option[String],
                           addressLine5: Option[String],
                           postcode:     Option[String]
)

object CustomerDetails {
  val validCustomerDetails: CustomerDetails = CustomerDetails(
    Some("John"),
    Some("Smith"),
    Some("07"),
    Some("12"),
    Some("1968"),
    Some("10 Jellybean Avenue"),
    Some("Brighton"),
    Some("West Sussex"),
    Some("United Kingdom"),
    Some("UK"),
    Some("SW190JK")
  )

}
