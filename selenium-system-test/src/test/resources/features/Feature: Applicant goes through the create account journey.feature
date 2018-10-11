Feature: Applicant goes through the create account journey
  @wip
  Scenario: Internal operator creates an account for an eligible applicant
    Given the operator is logged in
    And an applicant is eligible
    When the internal operator chooses to create an account on behalf of the applicant
    Then an account is successfully created

  Scenario: Internal operator cancels out of creating an account for an eligible applicant on the create-account screen
    Given the operator is logged in
    And an applicant is eligible
    When the internal operator is in the process of creating an account on behalf of the applicant
    And they cancel out of creating an account on the create account screen and choose to finish the call
    Then they have the option to enter a new applicant's NINO on the opening screen

  Scenario: Internal operator cancels out of creating an account for an eligible applicant when asked to confirm applicant's details
    Given the operator is logged in
    And an applicant is eligible
    When the internal operator is in the process of creating an account on behalf of the applicant
    And they cancel out of creating an account when asked to confirm the applicant's details and choose to finish the call
    Then they see the application has been cancelled

  Scenario: Eligibility check fails
    Given the operator is logged in
    When the eligibility service is down and an operator chooses to pass an applicant through the eligibility check
    Then they see a technical error
    And there was a button to go back

  Scenario: Account creation fails
    Given the operator is logged in
    Given the operator does an eligibility check when NS&I is down
    When the internal operator attempts to create an account on behalf of the applicant
    Then they see a technical error
    And there was a button to go back

  Scenario: Account already exists
    Given the operator is logged in
    When the operator does an eligibility check for an existing account holder
    Then they see account already exists message

  Scenario: Applicant is entitled to WTC but NOT in receipt of WTC and NOT in receipt of UC and so is NOT eligible
    Given the operator is logged in
    And the applicant has NINO ZX368514A
    When the internal operator does an eligibility check on behalf of the applicant
    Then they see that the applicant is NOT eligible for Help to Save with reason code 3
    When they choose to finish the call
    Then they have the option to enter a new applicant's NINO on the opening screen

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