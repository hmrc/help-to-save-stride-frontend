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

package uk.gov.hmrc.helptosavestridefrontend.forms

import java.time.{Clock, Instant, LocalDate, ZoneId}

import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.data.format.Formatter
import play.api.data.{Form, FormError}
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetailsValidation.ErrorMessages
import uk.gov.hmrc.helptosavestridefrontend.views.ApplicantDetailsForm.Ids

class ApplicantDetailsFormSpec extends AnyWordSpec with Matchers with MockFactory {

  implicit val clock: Clock = Clock.fixed(Instant.EPOCH, ZoneId.of("Z"))

  trait TestBinder {
    def bind[A](key: String, data: Map[String, String]): Either[Seq[FormError], A]
  }

  def mockBind[A](expectedKey: String)(result: Either[Seq[FormError], A]) =
    (binder.bind[A](_: String, _: Map[String, String]))
      .expects(expectedKey, *)
      .returning(result)
  val binder = mock[TestBinder]

  implicit val testValidation: ApplicantDetailsValidation = new ApplicantDetailsValidation {
    def dummyFormatter[A]: Formatter[A] = new Formatter[A] {
      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], A] = binder.bind(key, data)

      override def unbind(key: String, value: A): Map[String, String] = Map.empty[String, String]
    }
  }

  val emptyForm = ApplicantDetailsForm.applicantDetailsForm

  "ApplicantDetailsForm" must {

    "have a mapping" which {

      val today = LocalDate.now(clock)

        def mockValidInput(dateOfBirth: LocalDate): Unit = inSequence {
          mockBind[String](Ids.forename)(Right(""))
          mockBind[String](Ids.surname)(Right(""))
          mockBind[Int](Ids.dobDay)(Right(dateOfBirth.getDayOfMonth))
          mockBind[Int](Ids.dobMonth)(Right(dateOfBirth.getMonthValue))
          mockBind[Int](Ids.dobYear)(Right(dateOfBirth.getYear))
          mockBind[LocalDate](Ids.dateOfBirth)(Right(dateOfBirth))
          mockBind[String](Ids.address1)(Right(""))
          mockBind[String](Ids.address2)(Right(""))
          mockBind[Option[String]](Ids.address3)(Right(None))
          mockBind[Option[String]](Ids.address4)(Right(None))
          mockBind[Option[String]](Ids.address5)(Right(None))
          mockBind[String](Ids.postcode)(Right(""))
        }

      "allows valid input" in {
        mockValidInput(today.minusDays(1L))

        emptyForm.bind(Map(Ids.countryCode -> "GB")).errors shouldBe Seq.empty[FormError]
      }

      "does not allow an empty country code" in {
        mockValidInput(today.minusDays(1L))

        emptyForm.bind(Map.empty[String, String]).errors.map(_.key) shouldBe Seq(Ids.countryCode)
      }

      "have a mapping which can tell if the date of birth is in the future" in {
        mockValidInput(today.plusDays(1L))
        emptyForm.bind(Map(Ids.countryCode -> "GB")).errors shouldBe Seq(FormError(Ids.dateOfBirth, ErrorMessages.afterMax))
      }
    }

  }

}
