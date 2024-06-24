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

import java.time.{Clock, Instant, LocalDate, ZoneId}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.data.FormError
import play.api.{Configuration, Environment}
import uk.gov.hmrc.helptosavestridefrontend.TestSupport
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetailsValidation.ErrorMessages
import play.api.data.format.Formatter
import uk.gov.hmrc.helptosavestridefrontend.forms.DateFormFormatter
import uk.gov.hmrc.helptosavestridefrontend.views.ApplicantDetailsForm.Ids
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class ApplicantDetailsValidationSpec
    extends TestSupport with ScalaCheckDrivenPropertyChecks with ValidationTestSupport {

  val epochClock = Clock.fixed(Instant.EPOCH, ZoneId.of("Z"))

  override lazy val additionalConfig: Configuration =
    Configuration(
      "applicant-details.forename.max-length"      -> 3,
      "applicant-details.surname.max-length"       -> 3,
      "applicant-details.address-lines.max-length" -> 3,
      "applicant-details.postcode.max-length"      -> 3
    )

  val validation = new ApplicantDetailsValidationImpl(
    new FrontendAppConfig(
      fakeApplication.configuration,
      fakeApplication.injector.instanceOf[ServicesConfig],
      fakeApplication.injector.instanceOf[Environment]
    ),
    epochClock
  )

  "ApplicationDetailsValidation" when {

    val validateDate: Formatter[LocalDate] = DateFormFormatter.dateFormFormatter(
      maximumDateInclusive = Some(LocalDate.now()),
      minimumDateInclusive = Some(LocalDate.of(1900, 1, 1)),
      "dob-day",
      "dob-month",
      "dob-year",
      "dob",
      tooRecentArgs = Seq("today"),
      tooFarInPastArgs = Seq.empty
    )

    "validating forenames" must {

      lazy val testForename = testValidation[String](validation.nameFormatter) _

      "mark names as valid" when {

        "the name is non-empty and less than the configured maximum length" in {
          testForename(Some("a"))(Right("a"))
          testForename(Some(" abc "))(Right("abc"))
        }

      }

      "mark names as invalid" when {

        "the name doesn't exist" in {
          testForename(None)(Left(Set(ErrorMessages.isRequired)))
        }

        "the name is the empty string" in {
          testForename(Some(""))(Left(Set(ErrorMessages.isRequired)))
        }

        "the name is longer than the configured maximum length" in {
          testForename(Some("abcd"))(Left(Set(ErrorMessages.tooLong)))
        }

      }

    }

    "validating surnames" must {

      lazy val testSurname = testValidation[String](validation.nameFormatter) _

      "mark names as valid" when {

        "the name is non-empty and less than the configured maximum length" in {
          testSurname(Some("a"))(Right("a"))
          testSurname(Some(" abc "))(Right("abc"))
        }

      }

      "mark names as invalid" when {

        "the name doesn't exist" in {
          testSurname(None)(Left(Set(ErrorMessages.isRequired)))
        }

        "the name is the empty string" in {
          testSurname(Some(""))(Left(Set(ErrorMessages.isRequired)))
        }

        "the name is longer than the configured maximum length" in {
          testSurname(Some("abcd"))(Left(Set(ErrorMessages.tooLong)))
        }

      }

    }

    "validating days" must {

      def testDay(day: Option[String]) =
        validateDate.bind(
          Ids.dateOfBirth,
          Map(
            Ids.dobDay   -> day.getOrElse(""),
            Ids.dobMonth -> "12",
            Ids.dobYear  -> "2000"
          )
        )
      "mark days as invalid" when {

        "the day does not exist" in {
          testDay(None) shouldBe Left(Seq(FormError(Ids.dobDay, ErrorMessages.dayRequired)))
        }

        "the day is less than 1" in {
          testDay(Some("-1")) shouldBe Left(Seq(FormError(Ids.dobDay, ErrorMessages.isInvalid)))
        }

        "the day is greater than 31" in {
          testDay(Some("32")) shouldBe Left(Seq(FormError(Ids.dobDay, ErrorMessages.isInvalid)))
        }

        "the day is not an int" in {
          testDay(Some("hello")) shouldBe Left(Seq(FormError(Ids.dobDay, ErrorMessages.isInvalid)))
        }

      }

    }

    "validating months" must {

      def testMonth(month: Option[String]) =
        validateDate.bind(
          Ids.dateOfBirth,
          Map(
            Ids.dobDay   -> "1",
            Ids.dobMonth -> month.getOrElse(""),
            Ids.dobYear  -> "1900"
          )
        )

      "mark months as valid" when {

        "the month exists and is between 1 and 12" in {
          (1 to 12).foreach { d =>
            testMonth(Some(d.toString)) shouldBe Right(LocalDate.of(1900, d, 1))
            testMonth(Some(s" ${d.toString} ")) shouldBe Right(LocalDate.of(1900, d, 1))
          }
        }

      }

      "mark month as invalid" when {

        "the month does not exist" in {
          testMonth(None) shouldBe Left(Seq(FormError(Ids.dobMonth, ErrorMessages.monthRequired)))
        }

        "the month is less than 1" in {
          testMonth(Some("0")) shouldBe Left(Seq(FormError(Ids.dobMonth, ErrorMessages.isInvalid)))
        }

        "the month is greater than 12" in {
          testMonth(Some("13")) shouldBe Left(Seq(FormError(Ids.dobMonth, ErrorMessages.isInvalid)))
        }

        "the month is not an int" in {
          testMonth(Some("hi")) shouldBe Left(Seq(FormError(Ids.dobMonth, ErrorMessages.isInvalid)))
        }

      }

    }

    "validating years" must {
      def testYear(year: Option[String]) =
        validateDate.bind(
          Ids.dateOfBirth,
          Map(
            Ids.dobDay   -> "1",
            Ids.dobMonth -> "12",
            Ids.dobYear  -> year.getOrElse("")
          )
        )
      val currentYear = epochClock.instant().atZone(ZoneId.of("Z")).getYear

      "mark years as valid" when {

        "the year exists and is between 1900 and the current year" in {
          (1900 until currentYear).foreach { d =>
            testYear(Some(d.toString)) shouldBe Right(LocalDate.of(d, 12, 1))
            testYear(Some(s" ${d.toString} ")) shouldBe Right(LocalDate.of(d, 12, 1))
          }
        }

      }

      "mark years as invalid" when {

        "the year does not exist" in {
          testYear(None) shouldBe Left(Seq(FormError(Ids.dobYear, ErrorMessages.yearRequired)))
        }

        "the year is less than 1900" in {
          testYear(Some("1899")) shouldBe Left(Seq(FormError(Ids.dobYear, ErrorMessages.beforeMin)))
        }

        "the year is greater than the current year" in {
          testYear(Some((LocalDate.now().getYear + 1).toString)) shouldBe Left(
            List(FormError(Ids.dateOfBirth, List(ErrorMessages.afterMax), List("today")))
          )
        }

        "the year is not an int" in {
          testYear(Some("hullo")) shouldBe Left(Seq(FormError(Ids.dobYear, ErrorMessages.isInvalid)))
        }

      }

    }

    "validating date of births" must {

      val data = Map(Ids.dobDay -> "1", Ids.dobMonth -> "2", Ids.dobYear -> "1905")

      "mark years as valid" when {

        "the day, month and year values form a valid date" in {
          val result = validateDate.bind(Ids.dateOfBirth, data)

          result.map(_.getDayOfMonth) shouldBe Right(1)
          result.map(_.getMonthValue) shouldBe Right(2)
          result.map(_.getYear) shouldBe Right(1905)
        }

      }

      "mark days as valid" when {

        "the day exists and is between 1 and 31" in {
          (1 to 31).foreach { d =>
            validateDate.bind(
              Ids.dateOfBirth,
              Map(Ids.dobDay -> d.toString, Ids.dobMonth -> "12", Ids.dobYear -> "2000")
            ) shouldBe Right(LocalDate.of(2000, 12, d))
          }
        }

      }

      "mark fields as invalid" when {

        def testDateOfBirthInvalid(data: Map[String, String], expectedError: FormError): Unit =
          validateDate.bind(Ids.dateOfBirth, data) shouldBe Left(Seq(expectedError))

        "the day is missing" in {
          testDateOfBirthInvalid(data - Ids.dobDay, FormError(Ids.dobDay, ErrorMessages.dayRequired))
        }

        "the month is missing" in {
          testDateOfBirthInvalid(data - Ids.dobMonth, FormError(Ids.dobMonth, ErrorMessages.monthRequired))
        }

        "the year is missing" in {
          testDateOfBirthInvalid(data - Ids.dobYear, FormError(Ids.dobYear, ErrorMessages.yearRequired))
        }

        "the day, month and year values together do not form a valid date" in {
          // 31st February doesn't exist
          testDateOfBirthInvalid(
            Map(Ids.dobDay -> "31", Ids.dobMonth -> "2", Ids.dobYear -> "1990"),
            FormError(Ids.dateOfBirth, ErrorMessages.isInvalid)
          )
        }

      }

    }

    "validating address lines 1 and 2" must {

      lazy val testAddressLine1 = testValidation[String](validation.addressLineFormatter) _
      lazy val testAddressLine2 = testValidation[String](validation.addressLineFormatter) _

      "mark address lines as valid" when {

        "the address lines are non empty and their length are within than the configured maximum length" in {
          testAddressLine1(Some("a"))(Right("a"))
          testAddressLine1(Some(" abc "))(Right("abc"))

          testAddressLine2(Some("a"))(Right("a"))
          testAddressLine2(Some(" abc "))(Right("abc"))
        }

      }

      "mark names as invalid" when {

        "the address lines don't exist" in {
          testAddressLine1(None)(Left(Set(ErrorMessages.isRequired)))
          testAddressLine2(None)(Left(Set(ErrorMessages.isRequired)))
        }

        "the address lines are the empty string" in {
          testAddressLine1(Some(""))(Left(Set(ErrorMessages.isRequired)))
          testAddressLine2(Some(""))(Left(Set(ErrorMessages.isRequired)))
        }

        "the address lines are longer than the configured maximum length" in {
          testAddressLine1(Some("abcd"))(Left(Set(ErrorMessages.tooLong)))
          testAddressLine2(Some("abcd"))(Left(Set(ErrorMessages.tooLong)))
        }

      }

    }

    "validating address lines 3, 4 and 5" must {

      lazy val testAddressLine3 = testValidation[Option[String]](validation.addressOptionalLineFormatter) _
      lazy val testAddressLine4 = testValidation[Option[String]](validation.addressOptionalLineFormatter) _
      lazy val testAddressLine5 = testValidation[Option[String]](validation.addressOptionalLineFormatter) _

      "mark address lines as valid" when {

        "the address lines are non empty and their length are within than the configured maximum length" in {
          testAddressLine3(Some("a"))(Right(Some("a")))
          testAddressLine3(Some(" abc "))(Right(Some("abc")))

          testAddressLine4(Some("a"))(Right(Some("a")))
          testAddressLine4(Some(" abc "))(Right(Some("abc")))

          testAddressLine5(Some("a"))(Right(Some("a")))
          testAddressLine5(Some(" abc "))(Right(Some("abc")))
        }

        "the address lines are empty" in {
          testAddressLine3(Some(""))(Right(None))
          testAddressLine4(Some(""))(Right(None))
          testAddressLine5(Some(""))(Right(None))
        }

        "the address lines don't exist" in {
          testAddressLine3(None)(Right(None))
          testAddressLine4(None)(Right(None))
          testAddressLine5(None)(Right(None))
        }

      }

      "mark address lines as invalid" when {

        "the address lines are longer than the configured maximum length" in {
          testAddressLine3(Some("abcd"))(Left(Set(ErrorMessages.tooLong)))
          testAddressLine4(Some("abcd"))(Left(Set(ErrorMessages.tooLong)))
          testAddressLine5(Some("abcd"))(Left(Set(ErrorMessages.tooLong)))
        }

      }

    }

    "validating postcodes" must {

      lazy val testPostcode = testValidation[String](validation.postcodeFormatter) _

      "mark postcodes as valid" when {

        "they are non empty and their length are within the configured maximum length" in {
          testPostcode(Some("a"))(Right("a"))
          testPostcode(Some(" abc "))(Right("abc"))
          testPostcode(Some(" ab c "))(Right("ab c"))
        }

      }

      "mark names as invalid" when {

        "they don't exist" in {
          testPostcode(None)(Left(Set(ErrorMessages.isRequired)))
        }

        "they are the empty string" in {
          testPostcode(Some(""))(Left(Set(ErrorMessages.isRequired)))
        }

        "their length is greater than the configured maximum" in {
          testPostcode(Some("abcd"))(Left(Set(ErrorMessages.tooLong)))
        }

      }

    }

  }

}
