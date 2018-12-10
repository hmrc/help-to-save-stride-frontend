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

package uk.gov.hmrc.helptosavestridefrontend.forms

import java.time.{Clock, ZoneId}

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated}
import cats.syntax.apply._
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.data.{FormError, Mapping}
import play.api.data.Forms.{number, text}
import play.api.data.format.Formatter
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetailsValidation.ErrorMessages
import uk.gov.hmrc.helptosavestridefrontend.util.Validation.{ValidOrErrorStrings, _}
import uk.gov.hmrc.helptosavestridefrontend.util.TryOps._

import scala.util.Try

@ImplementedBy(classOf[ApplicantDetailsValidationImpl])
trait ApplicantDetailsValidation {
  val forenameFormatter: Formatter[String]
  val surnameFormatter: Formatter[String]
  val dayOfMonthFormatter: Formatter[Int]
  val monthFormatter: Formatter[Int]
  val yearFormatter: Formatter[Int]
  val addressLine1Formatter: Formatter[String]
  val addressLine2Formatter: Formatter[String]
  val addressLine3Formatter: Formatter[Option[String]]
  val addressLine4Formatter: Formatter[Option[String]]
  val addressLine5Formatter: Formatter[Option[String]]
  val postcodeFormatter: Formatter[String]
}

@Singleton
class ApplicantDetailsValidationImpl @Inject() (configuration: FrontendAppConfig, clock: Clock) extends ApplicantDetailsValidation {
  import configuration.FormValidation._

  val forenameFormatter: Formatter[String] = stringFormatter(
    { forename ⇒
      val trimmed = forename.trim
      val tooLongCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.length <= forenameMaxTotalLength, ErrorMessages.forenameTooLong)
      val tooShortCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.nonEmpty, ErrorMessages.forenameEmpty)

      (tooLongCheck, tooShortCheck).mapN{ case _ ⇒ trimmed }
    },
    ErrorMessages.forenameEmpty
  )

  val surnameFormatter: Formatter[String] = stringFormatter(
    { surname ⇒
      val trimmed = surname.trim
      val tooLongCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.length <= surnameMaxTotalLength, ErrorMessages.surnameTooLong)
      val tooShortCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.nonEmpty, ErrorMessages.surnameEmpty)

      (tooLongCheck, tooShortCheck).mapN{ case _ ⇒ trimmed }
    },
    ErrorMessages.surnameEmpty
  )

  val dayOfMonthFormatter: Formatter[Int] =
    intFormatter(1, () ⇒ 31, // scalastyle:ignore magic.number
      ErrorMessages.dayOfMonthInvalid, ErrorMessages.dayOfMonthInvalid, ErrorMessages.dayOfMonthInvalid, ErrorMessages.dayOfMonthEmpty)

  val monthFormatter: Formatter[Int] =
    intFormatter(1, () ⇒ 12, // scalastyle:ignore magic.number
      ErrorMessages.monthInvalid, ErrorMessages.monthInvalid, ErrorMessages.monthInvalid, ErrorMessages.monthEmpty)

  val yearFormatter: Formatter[Int] =
    intFormatter(1900, () ⇒ clock.instant().atZone(ZoneId.of("Z")).getYear, // scalastyle:ignore magic.number
      ErrorMessages.dateOfBirthInFuture, ErrorMessages.yearTooEarly, ErrorMessages.yearInvalid, ErrorMessages.yearEmpty)

  val addressLine1Formatter: Formatter[String] = mandatoryAddressLineValidator(ErrorMessages.address1TooLong, ErrorMessages.address1Empty)
  val addressLine2Formatter: Formatter[String] = mandatoryAddressLineValidator(ErrorMessages.address2TooLong, ErrorMessages.address2Empty)
  val addressLine3Formatter: Formatter[Option[String]] = optionalAddressLineValidator(ErrorMessages.address3TooLong)
  val addressLine4Formatter: Formatter[Option[String]] = optionalAddressLineValidator(ErrorMessages.address4TooLong)
  val addressLine5Formatter: Formatter[Option[String]] = optionalAddressLineValidator(ErrorMessages.address5TooLong)

  val postcodeFormatter: Formatter[String] = stringFormatter(
    { postcode ⇒
      val trimmed = postcode.replaceAllLiterally(" ", "")
      val tooLongCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.length <= postcodeMaxTotalLength, ErrorMessages.postcodeTooLong)
      val tooShortCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.nonEmpty, ErrorMessages.postCodeEmpty)

      (tooLongCheck, tooShortCheck).mapN{ case _ ⇒ postcode.trim }
    },
    ErrorMessages.postCodeEmpty
  )

  implicit def toFormErrorSeq[A](keyAndValidated: (String, Validated[NonEmptyList[String], A])): Either[Seq[FormError], A] = {
    val (key, validated) = keyAndValidated
    validated.toEither.leftMap(_.map(e ⇒ FormError(key, e)).toList)
  }

  private def formatter[A](validate:          String ⇒ Validated[NonEmptyList[String], A],
                           emptyErrorMessage: String,
                           mapping:           Mapping[A]
  ): Formatter[A] = new Formatter[A] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], A] =
      key → data.get(key).fold(invalid[A](emptyErrorMessage))(validate)

    override def unbind(key: String, value: A): Map[String, String] = mapping.withPrefix(key).unbind(value)
  }

  private def stringFormatter(validate:          String ⇒ Validated[NonEmptyList[String], String],
                              emptyErrorMessage: String): Formatter[String] =
    formatter(validate, emptyErrorMessage, text)

  private def intFormatter(minValue:                  Int,
                           maxValue:                  () ⇒ Int,
                           tooBigErrorMessage:        String,
                           tooSmallErrorMessage:      String,
                           invalidFormatErrorMessage: String,
                           emptyErrorMessage:         String): Formatter[Int] =
    formatter(
      { string ⇒
        val trimmed = string.trim
        if (trimmed.isEmpty) {
          invalid[Int](emptyErrorMessage)
        } else {
          Try(trimmed.toInt).fold(
            _ ⇒ invalid[Int](invalidFormatErrorMessage),
            { int ⇒
              val tooBigCheck: ValidOrErrorStrings[Int] = validatedFromBoolean(int)(_ <= maxValue(), tooBigErrorMessage)
              val tooSmallCheck: ValidOrErrorStrings[Int] = validatedFromBoolean(int)(_ >= minValue, tooSmallErrorMessage)

              (tooBigCheck, tooSmallCheck).mapN{ case _ ⇒ int }
            }
          )
        }
      },
      emptyErrorMessage, number
    )

  private def mandatoryAddressLineValidator(tooLongErrorMessage: String, emptyErrorMessage: String): Formatter[String] =
    stringFormatter({
      line ⇒
        val trimmed = line.trim
        val tooLongCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.length <= addressLineMaxTotalLength, tooLongErrorMessage)
        val tooShortCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.nonEmpty, emptyErrorMessage)

        (tooLongCheck, tooShortCheck).mapN{ case _ ⇒ trimmed }
    }, emptyErrorMessage)

  private def optionalAddressLineValidator(tooLongErrorMessage: String): Formatter[Option[String]] = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] =
      key → {
        data.get(key).map(_.trim).filter(_.nonEmpty) match {
          case Some(trimmed) ⇒ validatedFromBoolean(trimmed)(_.length <= addressLineMaxTotalLength, tooLongErrorMessage).map(Some(_))
          case None          ⇒ Valid(None)
        }
      }

    override def unbind(key: String, value: Option[String]): Map[String, String] =
      value.fold(Map.empty[String, String])(v ⇒ text.withPrefix(key).unbind(v))
  }
}

object ApplicantDetailsValidation {

  private[forms] object ErrorMessages {
    val forenameTooLong: String = "forename_too_long"
    val forenameEmpty: String = "forename_empty"

    val surnameTooLong: String = "surname_too_long"
    val surnameEmpty: String = "surname_empty"

    val dayOfMonthEmpty: String = "day_of_month_empty"
    val dayOfMonthInvalid: String = "day_of_month_invalid"

    val monthEmpty: String = "month_empty"
    val monthInvalid: String = "month_invalid"

    val yearEmpty: String = "year_empty"
    val yearInvalid: String = "year_invalid"
    val yearTooEarly: String = "year_too_early"

    val dateOfBirthInFuture: String = "date_of_birth_in_future"

    val address1TooLong: String = "address_1_too_long"
    val address1Empty: String = "address_1_empty"
    val address2TooLong: String = "address_2_too_long"
    val address2Empty: String = "address_2_empty"
    val address3TooLong: String = "address_3_too_long"
    val address4TooLong: String = "address_4_too_long"
    val address5TooLong: String = "address_5_too_long"

    val postcodeTooLong: String = "postcode_too_long"
    val postCodeEmpty: String = "postcode_empty"
  }

}
