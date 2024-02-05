/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavestridefrontend.models

import play.api.libs.json.{JsObject, Json}

import scala.io.Source

object CountryCode {

  type AlphaTwoCode = String
  type CountryName = String

  val countryCodes: List[(CountryName, AlphaTwoCode)] = {
    val content = Source.fromInputStream(getClass.getResourceAsStream("/resources/country.json")).mkString
    Json.parse(content) match {
      case JsObject(fields) =>
        fields.toList
          .map(x => ((x._2 \ "short_name").asOpt[String], (x._2 \ "alpha_two_code").asOpt[String]))
          .collect {
            case (Some(countryName), Some(countryCode)) =>
              countryName -> countryCode.take(2)
          }
      case _ => sys.error("no country codes were found, terminating the service")
    }
  }

}
