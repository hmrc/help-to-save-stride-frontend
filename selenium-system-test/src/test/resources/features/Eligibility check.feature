Feature: Operator performs an eligibility check on behalf of an applicant

  Scenario Outline: Applicant is entitled to WTC but NOT in receipt of WTC and NOT in receipt of UC and so is NOT eligible
    Given the operator is logged in with <role>
    And the applicant has NINO ZX368514A
    When the internal operator does an eligibility check on behalf of the applicant
    Then they see that the applicant is NOT eligible for Help to Save with reason code 3
    When they choose to finish the call
    Then they have the option to enter a new applicant's NINO on the opening screen
    Examples:
      | role                 |
      | hts_helpdesk_advisor |

  Scenario: Applicant is entitled to WTC but NOT in receipt of WTC and in receipt of UC but income is insufficient and so is NOT eligible
    Given the operator is logged in
    And the applicant has NINO EK978215B
    When the internal operator does an eligibility check on behalf of the applicant
    Then they see that the applicant is NOT eligible for Help to Save with reason code 4
    When they choose to finish the call
    Then they have the option to enter a new applicant's NINO on the opening screen

  Scenario: Applicant is NOT entitled to WTC and in receipt of UC but income is insufficient and so is NOT eligible
    Given the operator is logged in
    And the applicant has NINO HR156614D
    When the internal operator does an eligibility check on behalf of the applicant
    Then they see that the applicant is NOT eligible for Help to Save with reason code 5
    When they choose to finish the call
    Then they have the option to enter a new applicant's NINO on the opening screen

  Scenario: Applicant is NOT entitled to WTC and NOT in receipt of UC and so is NOT eligible
    Given the operator is logged in
    And the applicant has NINO LW634114A
    When the internal operator does an eligibility check on behalf of the applicant
    Then they see that the applicant is NOT eligible for Help to Save with reason code 9
    When they choose to finish the call
    Then they have the option to enter a new applicant's NINO on the opening screen

  Scenario: Eligibility check fails
    Given the operator is logged in
    When the eligibility service is down and an operator performs an eligibility check
    Then they see a technical error
    And there was a button to go back

  Scenario Outline: Account already exists
    Given the operator is logged in with <role>
    When the operator does an eligibility check for an existing account holder
    Then they see account already exists message
    Examples:
      | role                 |
      | hts_helpdesk_advisor |