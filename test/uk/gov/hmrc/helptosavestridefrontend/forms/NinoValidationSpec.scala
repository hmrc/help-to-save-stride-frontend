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

import cats.syntax.either._
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}
import play.api.Logger
import play.api.data.FormError
import uk.gov.hmrc.helptosavestridefrontend.forms.NINOValidation.ErrorMessages

// scalastyle:off magic.number
class NinoValidationSpec extends WordSpec with Matchers with GeneratorDrivenPropertyChecks {

  "EmailValidation" must {

      def genString(length: Int) = Gen.listOfN(length, Gen.alphaChar).map(_.mkString(""))

      def test(ninoValidation: NINOValidation)(value: String)(expectedResult: Either[Set[String], Unit], log: Boolean = false): Unit = {
        val result: Either[Seq[FormError], String] = ninoValidation.ninoFormatter.bind("key", Map("key" → value))
        if (log) Logger.error(value + ": " + result.toString)
        result.leftMap(_.toSet) shouldBe expectedResult.bimap(_.map(s ⇒ FormError("key", s)), _ ⇒ value)
      }

    val ninoValidation = new NINOValidation()

    "validate against valid ninos" in {
      test(ninoValidation)("AE123456C")(Right())
    }

    "validate against blank strings" in {
      test(ninoValidation)("")(Left(Set(ErrorMessages.invalidNinoPattern)))
    }

    "validate against in-valid patterns" in {
      forAll(genString(5), genString(5)) { (l, d) ⇒
        test(ninoValidation)(s"$l@$d")(Left(Set(ErrorMessages.invalidNinoPattern)))
      }
    }
  }

}

// scalastyle:on magic.number

