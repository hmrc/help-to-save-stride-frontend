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

package uk.gov.hmrc.helptosavestridefrontend.models

import play.api.libs.json.{JsResult, JsValue, Json, Reads}

sealed trait EnrolmentStatus

object EnrolmentStatus {

  case object Enrolled extends EnrolmentStatus

  case object NotEnrolled extends EnrolmentStatus

  implicit val enrolmentStatusReads: Reads[EnrolmentStatus] = new Reads[EnrolmentStatus] {

    case class EnrolmentStatusJSON(enrolled: Boolean, itmpHtSFlag: Boolean)

    implicit val enrolmentStatusJSONReads: Reads[EnrolmentStatusJSON] = Json.reads[EnrolmentStatusJSON]

    override def reads(json: JsValue): JsResult[EnrolmentStatus] =
      Json.fromJson[EnrolmentStatusJSON](json).map { result ⇒
        if (result.enrolled) {
          EnrolmentStatus.Enrolled
        } else {
          EnrolmentStatus.NotEnrolled
        }

      }
  }
}
