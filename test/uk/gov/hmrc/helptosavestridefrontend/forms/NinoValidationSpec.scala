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

import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.helptosavestridefrontend.forms.NINOValidation.{ErrorMessages, ninoFormatter}

// scalastyle:off magic.number
class NinoValidationSpec extends WordSpec with Matchers with GeneratorDrivenPropertyChecks with ValidationTestSupport {

  "EmailValidation" must {

      def genString(length: Int) = Gen.listOfN(length, Gen.alphaChar).map(_.mkString(""))

    val testNino = testValidation[String](ninoFormatter) _

    "validate against valid ninos" in {
      testNino(Some("AE123456C"))(Right("AE123456C"))
      testNino(Some("AE 12 34 56 C"))(Right("AE123456C"))
      testNino(Some("ae12 34 56c"))(Right("AE123456C"))
    }

    "validate against blank strings" in {
      testNino(Some(""))(Left(Set(ErrorMessages.blankNINO)))
    }

    "validate against in-valid patterns" in {
      forAll(genString(5), genString(5)) { (l, d) ⇒
        testNino(Some(s"$l@$d"))(Left(Set(ErrorMessages.invalidNinoPattern)))
      }

      testNino(Some("JF677211D"))(Left(Set(ErrorMessages.invalidNinoPattern)))
      testNino(Some("jf677211d"))(Left(Set(ErrorMessages.invalidNinoPattern)))
      testNino(Some("AE123456"))(Left(Set(ErrorMessages.invalidNinoPattern)))

    }

    "validate against non-existent NINO's" in {
      testNino(None)(Left(Set(ErrorMessages.blankNINO)))
    }

  }

}

// scalastyle:on magic.number

