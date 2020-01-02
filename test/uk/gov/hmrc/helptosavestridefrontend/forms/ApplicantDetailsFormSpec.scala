/*
 * Copyright 2020 HM Revenue & Customs
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
import org.scalatest.{Matchers, WordSpec}
import play.api.data.format.Formatter
import play.api.data.{Form, FormError}
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetailsForm.ApplicantDetailsFormOps
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetailsValidation.ErrorMessages
import uk.gov.hmrc.helptosavestridefrontend.views.ApplicantDetailsForm.Ids

class ApplicantDetailsFormSpec extends WordSpec with Matchers with MockFactory {

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

    override val forenameFormatter: Formatter[String] = dummyFormatter[String]
    override val surnameFormatter: Formatter[String] = dummyFormatter[String]
    override val dayOfMonthFormatter: Formatter[Int] = dummyFormatter[Int]
    override val monthFormatter: Formatter[Int] = dummyFormatter[Int]
    override val yearFormatter: Formatter[Int] = dummyFormatter[Int]
    override val dateOfBirthFormatter: Formatter[LocalDate] = dummyFormatter[LocalDate]
    override val addressLine1Formatter: Formatter[String] = dummyFormatter[String]
    override val addressLine2Formatter: Formatter[String] = dummyFormatter[String]
    override val addressLine3Formatter: Formatter[Option[String]] = dummyFormatter[Option[String]]
    override val addressLine4Formatter: Formatter[Option[String]] = dummyFormatter[Option[String]]
    override val addressLine5Formatter: Formatter[Option[String]] = dummyFormatter[Option[String]]
    override val postcodeFormatter: Formatter[String] = dummyFormatter[String]
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

        emptyForm.bind(Map(Ids.countryCode → "GB")).errors shouldBe Seq.empty[FormError]
      }

      "does not allow an empty country code" in {
        mockValidInput(today.minusDays(1L))

        emptyForm.bind(Map.empty[String, String]).errors.map(_.key) shouldBe Seq(Ids.countryCode)
      }

      "have a mapping which can tell if the date of birth is in the future" in {
        mockValidInput(today.plusDays(1L))

        emptyForm.bind(Map(Ids.countryCode → "GB")).errors shouldBe Seq(FormError("", ErrorMessages.dateOfBirthInFuture))
      }
    }

    "have a method" which afterWord("says if the form has"){

        def test(errorFieldId: String, errorMessage: String)(containsError: Form[ApplicantDetails] ⇒ Boolean): Unit = {
          withClue(s"For errorFieldId and errorMessage [$errorFieldId, $errorMessage]: "){
            val errorForm = emptyForm.copy(errors = Seq(FormError(errorFieldId, errorMessage)))

            containsError(emptyForm) shouldBe false
            containsError(errorForm) shouldBe true
          }
        }

      "a forename which is too long" in {
        test(Ids.forename, ErrorMessages.forenameTooLong)(_.hasForenameTooLong)
      }

      "a forename which is empty" in {
        test(Ids.forename, ErrorMessages.forenameEmpty)(_.hasForenameEmpty)
      }

      "a surname which is too long" in {
        test(Ids.surname, ErrorMessages.surnameTooLong)(_.hasSurnameTooLong)
      }

      "a surname which is empty" in {
        test(Ids.surname, ErrorMessages.surnameEmpty)(_.hasSurnameEmpty)
      }

      "a day of month which is empty" in {
        test(Ids.dobDay, ErrorMessages.dayOfMonthEmpty)(_.hasDayOfMonthEmpty)
      }

      "a day of month which is invalid" in {
        test(Ids.dobDay, ErrorMessages.dayOfMonthInvalid)(_.hasDayOfMonthInvalid)
      }

      "a month which is empty" in {
        test(Ids.dobMonth, ErrorMessages.monthEmpty)(_.hasMonthEmpty)
      }

      "a month which is invalid" in {
        test(Ids.dobMonth, ErrorMessages.monthInvalid)(_.hasMonthInvalid)
      }

      "a year which is empty" in {
        test(Ids.dobYear, ErrorMessages.yearEmpty)(_.hasYearEmpty)
      }

      "a year which is invalid" in {
        test(Ids.dobYear, ErrorMessages.yearInvalid)(_.hasYearInvalid)
      }

      "a year which is too early" in {
        test(Ids.dobYear, ErrorMessages.yearTooEarly)(_.hasYearTooEarly)
      }

      "a date of birth which is in the future" in {
        test(Ids.dobYear, ErrorMessages.dateOfBirthInFuture)(_.hasDateOfBirthInFuture)
        test("", ErrorMessages.dateOfBirthInFuture)(_.hasDateOfBirthInFuture)
      }

      "a date of birth which is invalid" in {
        test(Ids.dateOfBirth, ErrorMessages.dateOfBirthInvalid)(_.hasDateOfBirthInvalid)
        test("", ErrorMessages.dateOfBirthInvalid)(_.hasDateOfBirthInvalid)
      }

      "an address line 1 which is too long" in {
        test(Ids.address1, ErrorMessages.address1TooLong)(_.hasAddress1TooLong)
      }

      "an address line 1 which is empty" in {
        test(Ids.address1, ErrorMessages.address1Empty)(_.hasAddress1Empty)
      }

      "an address line 2 which is too long" in {
        test(Ids.address2, ErrorMessages.address2TooLong)(_.hasAddress2TooLong)
      }

      "an address line 2 which is empty" in {
        test(Ids.address2, ErrorMessages.address2Empty)(_.hasAddress2Empty)
      }

      "an address line 3 which is too long" in {
        test(Ids.address3, ErrorMessages.address3TooLong)(_.hasAddress3TooLong)
      }

      "an address line 4 which is too long" in {
        test(Ids.address4, ErrorMessages.address4TooLong)(_.hasAddress4TooLong)
      }

      "an address line 5 which is too long" in {
        test(Ids.address5, ErrorMessages.address5TooLong)(_.hasAddress5TooLong)
      }

      "an postcode which is too long" in {
        test(Ids.postcode, ErrorMessages.postcodeTooLong)(_.hasPostcodeTooLong)
      }

      "an postcode which is empty" in {
        test(Ids.postcode, ErrorMessages.postCodeEmpty)(_.hasPostcodeEmpty)
      }

    }

  }

}
