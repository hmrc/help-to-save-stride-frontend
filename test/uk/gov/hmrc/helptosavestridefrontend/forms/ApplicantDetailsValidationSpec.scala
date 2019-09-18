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

package uk.gov.hmrc.helptosavestridefrontend.forms

import java.time.{Clock, Instant, LocalDate, ZoneId}

import cats.syntax.either._
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import play.api.data.FormError
import play.api.{Configuration, Environment}
import uk.gov.hmrc.helptosavestridefrontend.TestSupport
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetailsValidation.ErrorMessages
import uk.gov.hmrc.helptosavestridefrontend.views.ApplicantDetailsForm.Ids
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

class ApplicantDetailsValidationSpec extends TestSupport with GeneratorDrivenPropertyChecks with ValidationTestSupport {

  val epochClock = Clock.fixed(Instant.EPOCH, ZoneId.of("Z"))

  override lazy val additionalConfig: Configuration =
    Configuration(
      "applicant-details.forename.max-length" → 3,
      "applicant-details.surname.max-length" → 3,
      "applicant-details.address-lines.max-length" → 3,
      "applicant-details.postcode.max-length" → 3
    )

  lazy val validation = new ApplicantDetailsValidationImpl(
    new FrontendAppConfig(fakeApplication.injector.instanceOf[RunMode],
                          fakeApplication.configuration,
                          fakeApplication.injector.instanceOf[ServicesConfig],
                          fakeApplication.injector.instanceOf[Environment]),
    epochClock
  )

  "ApplicationDetailsValidation" when {

    "validating forenames" must {

      lazy val testForename = testValidation[String](validation.forenameFormatter) _

      "mark names as valid" when {

        "the name is non-empty and less than the configured maximum length" in {
          testForename(Some("a"))(Right("a"))
          testForename(Some(" abc "))(Right("abc"))
        }

      }

      "mark names as invalid" when {

        "the name doesn't exist" in {
          testForename(None)(Left(Set(ErrorMessages.forenameEmpty)))
        }

        "the name is the empty string" in {
          testForename(Some(""))(Left(Set(ErrorMessages.forenameEmpty)))
        }

        "the name is longer than the configured maximum length" in {
          testForename(Some("abcd"))(Left(Set(ErrorMessages.forenameTooLong)))
        }

      }

    }

    "validating surnames" must {

      lazy val testSurname = testValidation[String](validation.surnameFormatter) _

      "mark names as valid" when {

        "the name is non-empty and less than the configured maximum length" in {
          testSurname(Some("a"))(Right("a"))
          testSurname(Some(" abc "))(Right("abc"))
        }

      }

      "mark names as invalid" when {

        "the name doesn't exist" in {
          testSurname(None)(Left(Set(ErrorMessages.surnameEmpty)))
        }

        "the name is the empty string" in {
          testSurname(Some(""))(Left(Set(ErrorMessages.surnameEmpty)))
        }

        "the name is longer than the configured maximum length" in {
          testSurname(Some("abcd"))(Left(Set(ErrorMessages.surnameTooLong)))
        }

      }

    }

    "validating day of months" must {

      lazy val testDay = testValidation[Int](validation.dayOfMonthFormatter) _

      "mark days as valid" when {

        "the day exists and is between 1 and 31" in {
          (1 to 31).foreach { d ⇒
            testDay(Some(d.toString))(Right(d))
            testDay(Some(s" ${d.toString} "))(Right(d))
          }
        }

      }

      "mark days as invalid" when {

        "the day does not exist" in {
          testDay(None)(Left(Set(ErrorMessages.dayOfMonthEmpty)))
        }

        "the day is less than 1" in {
          testDay(Some("-1"))(Left(Set(ErrorMessages.dayOfMonthInvalid)))
        }

        "the day is greater than 31" in {
          testDay(Some("32"))(Left(Set(ErrorMessages.dayOfMonthInvalid)))
        }

        "the day is not an int" in {
          testDay(Some("hello"))(Left(Set(ErrorMessages.dayOfMonthInvalid)))
        }

      }

    }

    "validating months" must {

      lazy val testMonth = testValidation[Int](validation.monthFormatter) _

      "mark months as valid" when {

        "the month exists and is between 1 and 12" in {
          (1 to 12).foreach { d ⇒
            testMonth(Some(d.toString))(Right(d))
            testMonth(Some(s" ${d.toString} "))(Right(d))
          }
        }

      }

      "mark month as invalid" when {

        "the month does not exist" in {
          testMonth(None)(Left(Set(ErrorMessages.monthEmpty)))
        }

        "the month is less than 1" in {
          testMonth(Some("0"))(Left(Set(ErrorMessages.monthInvalid)))
        }

        "the month is greater than 12" in {
          testMonth(Some("13"))(Left(Set(ErrorMessages.monthInvalid)))
        }

        "the month is not an int" in {
          testMonth(Some("hi"))(Left(Set(ErrorMessages.monthInvalid)))
        }

      }

    }

    "validating years" must {

      lazy val testYear = testValidation[Int](validation.yearFormatter) _
      val currentYear = epochClock.instant().atZone(ZoneId.of("Z")).getYear

      "mark years as valid" when {

        "the year exists and is between 1900 and the current year" in {
          (1900 to currentYear).foreach { d ⇒
            testYear(Some(d.toString))(Right(d))
            testYear(Some(s" ${d.toString} "))(Right(d))
          }
        }

      }

      "mark years as invalid" when {

        "the year does not exist" in {
          testYear(None)(Left(Set(ErrorMessages.yearEmpty)))
        }

        "the year is less than 1900" in {
          testYear(Some("1899"))(Left(Set(ErrorMessages.yearTooEarly)))
        }

        "the year is greater than the current year" in {
          testYear(Some((currentYear + 1).toString))(Left(Set(ErrorMessages.dateOfBirthInFuture)))
        }

        "the year is not an int" in {
          testYear(Some("hullo"))(Left(Set(ErrorMessages.yearInvalid)))
        }

      }

    }

    "validating date of births" must {

      val data = Map(Ids.dobDay → "1", Ids.dobMonth → "2", Ids.dobYear → "1990")

      "mark years as valid" when {

        "the day, month and year values form a valid date" in {
          val result = validation.dateOfBirthFormatter.bind("", data)

          result.map(_.getDayOfMonth) shouldBe Right(1)
          result.map(_.getMonthValue) shouldBe Right(2)
          result.map(_.getYear) shouldBe Right(1990)
        }

      }

      "mark years as invalid" when {

          def testDateOfBirthInvalid(data: Map[String, String]): Unit =
            validation.dateOfBirthFormatter.bind("", data) shouldBe Left(Seq(FormError(Ids.dateOfBirth, ErrorMessages.dateOfBirthInvalid)))

        "the day is missing" in {
          testDateOfBirthInvalid(data - Ids.dobDay)
        }

        "the month is missing" in {
          testDateOfBirthInvalid(data - Ids.dobMonth)
        }

        "the year is missing" in {
          testDateOfBirthInvalid(data - Ids.dobYear)
        }

        "the day, month and year values together do not form a valid date" in {
          // 31st February doesn't exist
          testDateOfBirthInvalid(Map(Ids.dobDay → "31", Ids.dobMonth → "2", Ids.dobYear → "1990"))
        }

      }

    }

    "validating address lines 1 and 2" must {

      lazy val testAddressLine1 = testValidation[String](validation.addressLine1Formatter) _
      lazy val testAddressLine2 = testValidation[String](validation.addressLine2Formatter) _

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
          testAddressLine1(None)(Left(Set(ErrorMessages.address1Empty)))
          testAddressLine2(None)(Left(Set(ErrorMessages.address2Empty)))
        }

        "the address lines are the empty string" in {
          testAddressLine1(Some(""))(Left(Set(ErrorMessages.address1Empty)))
          testAddressLine2(Some(""))(Left(Set(ErrorMessages.address2Empty)))
        }

        "the address lines are longer than the configured maximum length" in {
          testAddressLine1(Some("abcd"))(Left(Set(ErrorMessages.address1TooLong)))
          testAddressLine2(Some("abcd"))(Left(Set(ErrorMessages.address2TooLong)))
        }

      }

    }

    "validating address lines 3, 4 and 5" must {

      lazy val testAddressLine3 = testValidation[Option[String]](validation.addressLine3Formatter) _
      lazy val testAddressLine4 = testValidation[Option[String]](validation.addressLine4Formatter) _
      lazy val testAddressLine5 = testValidation[Option[String]](validation.addressLine5Formatter) _

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
          testAddressLine3(Some("abcd"))(Left(Set(ErrorMessages.address3TooLong)))
          testAddressLine4(Some("abcd"))(Left(Set(ErrorMessages.address4TooLong)))
          testAddressLine5(Some("abcd"))(Left(Set(ErrorMessages.address5TooLong)))
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
          testPostcode(None)(Left(Set(ErrorMessages.postCodeEmpty)))
        }

        "they are the empty string" in {
          testPostcode(Some(""))(Left(Set(ErrorMessages.postCodeEmpty)))
        }

        "their length is greater than the configured maximum" in {
          testPostcode(Some("abcd"))(Left(Set(ErrorMessages.postcodeTooLong)))
        }

      }

    }

  }

}
