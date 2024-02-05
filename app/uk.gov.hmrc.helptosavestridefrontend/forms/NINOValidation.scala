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

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.string._
import cats.syntax.either._
import cats.syntax.eq._
import play.api.data.Forms.text
import play.api.data.format.Formatter
import play.api.data.{Form, FormError}

import scala.util.matching.Regex

object NINOValidation {

  val ninoFormatter: Formatter[String] = new Formatter[String] {

    private def invalid[A](message: String): ValidatedNel[String, A] = Invalid(NonEmptyList[String](message, Nil))

    private def validatedFromBoolean[A](a: A)(predicate: A => Boolean, ifFalse: => String): ValidatedNel[String, A] =
      if (predicate(a)) Valid(a) else invalid(ifFalse)

    val ninoRegex: Regex =
      """^((?!(BG|GB|KN|NK|NT|TN|ZZ)|(D|F|I|Q|U|V)[A-Z]|[A-Z](D|F|I|O|Q|U|V))[A-Z]{2})[0-9]{6}[A-D]{1}$""".r

    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], String] = {
      val validation: Validated[NonEmptyList[String], String] =
        data.get(key).filter(_.nonEmpty).fold(invalid[String](ErrorMessages.blankNINO)) { s =>
          validatedFromBoolean(s.toUpperCase.replaceAll(" ", ""))(
            _.matches(ninoRegex.regex),
            ErrorMessages.invalidNinoPattern)
        }

      validation.toEither.leftMap(_.map(e => FormError(key, e)).toList)
    }

    override def unbind(key: String, value: String): Map[String, String] =
      text.withPrefix(key).unbind(value)
  }

  private[forms] object ErrorMessages {

    val invalidNinoPattern: String = "invalid_nino_pattern"

    val blankNINO = "blank_nino"
  }

  implicit class FormOps(val f: Form[GiveNINO]) {

    def hasInvalidNINO: Boolean = f.error("nino").exists(_.message === ErrorMessages.invalidNinoPattern)

    def hasBlankNINO: Boolean = f.error("nino").exists(_.message === ErrorMessages.blankNINO)

  }

}
