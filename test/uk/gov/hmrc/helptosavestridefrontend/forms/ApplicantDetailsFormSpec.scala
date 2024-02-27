/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.helptosavestridefrontend.forms

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.data.{Form, FormError}
import play.api.test.Helpers.baseApplicationBuilder.injector
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetailsValidation.ErrorMessages
import uk.gov.hmrc.helptosavestridefrontend.views.ApplicantDetailsForm.Ids

import java.time.{Clock, Instant, ZoneId}

class ApplicantDetailsFormSpec extends AnyWordSpec with Matchers with MockFactory {

  implicit val clock: Clock = Clock.fixed(Instant.EPOCH, ZoneId.of("Z"))

  trait TestBinder {
    def bind[A](key: String, data: Map[String, String]): Either[Seq[FormError], A]
  }

  def mockBind[A](expectedKey: String)(result: Either[Seq[FormError], A]) =
    (binder
      .bind[A](_: String, _: Map[String, String]))
      .expects(expectedKey, *)
      .returning(result)
  val binder: TestBinder = mock[TestBinder]
  implicit lazy val frontendAppConfig: FrontendAppConfig = injector().instanceOf[FrontendAppConfig]
  implicit val testValidation: ApplicantDetailsValidation =
    new ApplicantDetailsValidationImpl(frontendAppConfig: FrontendAppConfig, clock: Clock)

  val applicantDetailsForm: Form[ApplicantDetails] = ApplicantDetailsForm.applicantDetailsForm

  "ApplicantDetailsForm" must {

    "have a mapping" which {

      "allows valid input" in {

        applicantDetailsForm
          .bind(Map(
            Ids.forename    -> "forename",
            Ids.surname     -> "surname",
            Ids.dobDay      -> "1",
            Ids.dobMonth    -> "12",
            Ids.dobYear     -> "1901",
            Ids.address1    -> "address1",
            Ids.address2    -> "address2",
            Ids.address3    -> "address3",
            Ids.address4    -> "address4",
            Ids.address5    -> "address5",
            Ids.postcode    -> "AB11 1CD",
            Ids.countryCode -> "GB"
          ))
          .errors shouldBe List.empty
      }

      "does not allow an empty country code" in {

        applicantDetailsForm
          .bind(Map(
            Ids.forename -> "forename",
            Ids.surname  -> "surname",
            Ids.dobDay   -> "1",
            Ids.dobMonth -> "12",
            Ids.dobYear  -> "1901",
            Ids.address1 -> "address1",
            Ids.address2 -> "address2",
            Ids.address3 -> "address3",
            Ids.address4 -> "address4",
            Ids.address5 -> "address5",
            Ids.postcode -> "AB11 1CD"
          ))
          .errors shouldBe Seq(FormError(Ids.countryCode, ErrorMessages.isRequired))
      }

      "have a mapping which can tell if the date of birth is in the future" in {

        applicantDetailsForm
          .bind(Map(
            Ids.forename    -> "forename",
            Ids.surname     -> "surname",
            Ids.dobDay      -> "1",
            Ids.dobMonth    -> "12",
            Ids.dobYear     -> "2002",
            Ids.address1    -> "address1",
            Ids.address2    -> "address2",
            Ids.address3    -> "address3",
            Ids.address4    -> "address4",
            Ids.address5    -> "address5",
            Ids.postcode    -> "AB11 1CD",
            Ids.countryCode -> "GB"
          ))
          .errors shouldBe Seq(FormError(Ids.dateOfBirth, List(ErrorMessages.afterMax), List("today")))
      }
    }

  }

}
