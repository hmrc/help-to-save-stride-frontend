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

package uk.gov.hmrc.helptosavestridefrontend.views.helpers

import play.api.i18n.Messages
import uk.gov.hmrc.govukfrontend.views.html.components._

import uk.gov.hmrc.helptosavestridefrontend.models.NSIPayload
import uk.gov.hmrc.helptosavestridefrontend.util.browserDateFormat

class SummaryRowsNoChange {
  def summaryListRow(
    question: String,
    answer: String
  ): SummaryListRow = SummaryListRow(
    key = Key(content = Text(question), classes = "govuk-!-width-one-third"),
    value = Value(content = HtmlContent(answer))
  )

  def userDetailsRow(details: NSIPayload)(implicit messages: Messages): List[SummaryListRow] = {
    val messageKey = "hts.you.are.eligible.user.details"

    val nameRow: SummaryListRow = summaryListRow(
      messages(s"$messageKey.name-label"),
      s"""${details.forename} ${details.surname}"""
    )

    val ninoRow: SummaryListRow = summaryListRow(
      messages(s"$messageKey.nino-label"),
      s"${details.nino.grouped(2).mkString(" ")}"
    )

    val dobRow: SummaryListRow = summaryListRow(
      messages(s"$messageKey.dob-label"),
      s"${details.dateOfBirth.format(browserDateFormat)}"
    )

    val addressRow: SummaryListRow = summaryListRow(
      messages(s"$messageKey.address-label"),
      s"""
        ${details.contactDetails.address1}<br>
        ${details.contactDetails.address2}<br>
        ${details.contactDetails.address3.fold("")(a => s"$a <br>")}
        ${details.contactDetails.address4.fold("")(a => s"$a <br>")}
        ${details.contactDetails.address5.fold("")(a => s"$a <br>")}
        ${details.contactDetails.postcode}
      """
    )

    List(
      nameRow,
      ninoRow,
      dobRow,
      addressRow
    )
  }

  def personalDetailsRow(details: NSIPayload)(implicit messages: Messages): List[SummaryListRow] = {
    val messageKey = "hts.you.are.eligible.user.details"

    val nameRow: SummaryListRow = summaryListRow(
      messages(s"$messageKey.name-label"),
      s"""${details.forename} ${details.surname}"""
    )

    val ninoRow: SummaryListRow = summaryListRow(
      messages(s"$messageKey.nino-label"),
      s"${details.nino.grouped(2).mkString(" ")}"
    )

    val dobRow: SummaryListRow = summaryListRow(
      messages(s"$messageKey.dob-label"),
      s"${details.dateOfBirth.format(browserDateFormat)}"
    )

    List(
      nameRow,
      ninoRow,
      dobRow
    )
  }

  def addressDetailsRow(details: NSIPayload)(implicit messages: Messages): List[SummaryListRow] = {
    val messageKey = "hts.you.are.eligible.user.details"

    val addressRow: SummaryListRow = summaryListRow(
      messages(s"$messageKey.address-label"),
      s"""
        ${details.contactDetails.address1}<br>
        ${details.contactDetails.address2}<br>
        ${details.contactDetails.address3.fold("")(a => s"$a <br>")}
        ${details.contactDetails.address4.fold("")(a => s"$a <br>")}
        ${details.contactDetails.address5.fold("")(a => s"$a <br>")}
        ${details.contactDetails.postcode}
      """
    )

    List(
      addressRow
    )
  }
}
