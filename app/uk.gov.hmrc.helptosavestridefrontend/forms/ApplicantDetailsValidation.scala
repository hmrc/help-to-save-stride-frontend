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

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated}
import cats.syntax.apply._
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.data.Forms.text
import play.api.data.format.Formatter
import play.api.data.{FormError, Mapping}
import uk.gov.hmrc.helptosavestridefrontend.config.FrontendAppConfig
import uk.gov.hmrc.helptosavestridefrontend.forms.ApplicantDetailsValidation.ErrorMessages
import uk.gov.hmrc.helptosavestridefrontend.util.Validation._

import java.time.Clock
import scala.language.implicitConversions

@ImplementedBy(classOf[ApplicantDetailsValidationImpl])
trait ApplicantDetailsValidation {
  val nameFormatter: Formatter[String]
  val addressLineFormatter: Formatter[String]
  val addressOptionalLineFormatter: Formatter[Option[String]]
  val postcodeFormatter: Formatter[String]
}

@Singleton
class ApplicantDetailsValidationImpl @Inject() (configuration: FrontendAppConfig, clock: Clock) extends ApplicantDetailsValidation {
  import configuration.FormValidation._

  val nameFormatter: Formatter[String] = stringFormatter(
    { forename =>
      val trimmed = forename.trim
      val tooLongCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.length <= forenameMaxTotalLength, ErrorMessages.tooLong)
      val tooShortCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.nonEmpty, ErrorMessages.isRequired)

      (tooLongCheck, tooShortCheck).mapN{ case _ => trimmed }
    }
  )

  val addressLineFormatter: Formatter[String] = mandatoryAddressLineValidator
  val addressOptionalLineFormatter: Formatter[Option[String]] = optionalAddressLineValidator
  val postcodeFormatter: Formatter[String] = stringFormatter(
    { postcode =>
      val trimmed = postcode.replaceAll(" ", "")
      val tooLongCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.length <= postcodeMaxTotalLength, ErrorMessages.tooLong)
      val tooShortCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.nonEmpty, ErrorMessages.isRequired)

      (tooLongCheck, tooShortCheck).mapN{ case _ => postcode.trim }
    }
  )

  implicit def toFormErrorSeq[A](keyAndValidated: (String, Validated[NonEmptyList[String], A])): Either[Seq[FormError], A] = {
    val (key, validated) = keyAndValidated
    validated.toEither.leftMap(_.map(e => FormError(key, e)).toList)
  }

  private def formatter[A](validate: String => Validated[NonEmptyList[String], A],
                           mapping:  Mapping[A]
  ): Formatter[A] = new Formatter[A] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], A] =
      key -> data.get(key).fold(invalid[A](ErrorMessages.isRequired))(validate)

    override def unbind(key: String, value: A): Map[String, String] = mapping.withPrefix(key).unbind(value)
  }

  private def stringFormatter(validate: String => Validated[NonEmptyList[String], String]): Formatter[String] =
    formatter(validate, text)

  private def mandatoryAddressLineValidator: Formatter[String] =
    stringFormatter({
      line =>
        val trimmed = line.trim
        val tooLongCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.length <= addressLineMaxTotalLength, ErrorMessages.tooLong)
        val tooShortCheck: ValidOrErrorStrings[String] = validatedFromBoolean(trimmed)(_.nonEmpty, ErrorMessages.isRequired)

        (tooLongCheck, tooShortCheck).mapN{ case _ => trimmed }
    })

  private def optionalAddressLineValidator: Formatter[Option[String]] = new Formatter[Option[String]] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Option[String]] =
      key -> {
        data.get(key).map(_.trim).filter(_.nonEmpty) match {
          case Some(trimmed) => validatedFromBoolean(trimmed)(_.length <= addressLineMaxTotalLength, ErrorMessages.tooLong).map(Some(_))
          case None          => Valid(None)
        }
      }

    override def unbind(key: String, value: Option[String]): Map[String, String] =
      value.fold(Map.empty[String, String])(v => text.withPrefix(key).unbind(v))
  }
}

object ApplicantDetailsValidation {

  private[forms] object ErrorMessages {
    val tooLong: String = "error.tooLong"
    val isRequired: String = "error.required"
    val isInvalid: String = "error.invalid"
    val beforeMin: String = "error.tooFarInPast"
    val afterMax: String = "error.tooFuture"
    val dayRequired: String = "error.dayRequired"
    val monthRequired: String = "error.monthRequired"
    val yearRequired: String = "error.yearRequired"
  }

}
