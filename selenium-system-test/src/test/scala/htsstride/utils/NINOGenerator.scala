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

package htsstride.utils

import uk.gov.hmrc.domain.Generator

import scala.util.Random

trait NINOGenerator {

  private var generator = new Generator()

  private var current = generator.nextNino.value

  private def generateNINO(): String = {
    current = generator.nextNino.value
    current
  }

  private def toEligible(nino: String) = "AE" + nino.drop(2)

  protected def reset(): Unit = {
    generator = new Generator()
    current = generator.nextNino.value
  }

  def generateEligibleNINO(): String = {
    current = toEligible(generateNINO())
    current
  }

  //Private Beta
  def generateIneligiblePrefix(): String = {
    val prefixes = Set("NE02", "NE03")
    val rnd = new Random
    prefixes.toVector(rnd.nextInt(prefixes.size))
  }

  def generateHTTPErrorCodePrefix(code: Int): String =
    "ES" + code.toString

  def generateIneligibleNINO(): String = {
    val ineligibleNino = generateIneligiblePrefix() + generateNINO().drop(4)
    current = ineligibleNino
    ineligibleNino
  }

  def generateHTTPErrorCodeNINO(code: Int): String = {
    val HTTPErrorCodeNino = generateHTTPErrorCodePrefix(code) + generateNINO().drop(5)
    current = HTTPErrorCodeNino
    HTTPErrorCodeNino
  }

  def currentNINO(): String = current

}
