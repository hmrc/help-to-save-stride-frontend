Feature: Applicant goes through the create account journey
  Scenario: Internal operator creates an account for an eligible applicant
    Given the operator is logged in
    And an applicant is eligible
    And the internal operator chooses to create an account on behalf of the applicant
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
    Then they have the option to enter a new applicant's NINO on the opening screen

  Scenario: Applicant is NOT eligible
    Given the operator is logged in
    And an applicant is NOT eligible
    When the internal operator does an eligibility check on behalf of the applicant
    Then they see that the applicant is NOT eligible
    When they choose the finish the call
    Then they have the option to enter a new applicant's NINO on the opening screen

  Scenario: Eligibility check fails
    Given the operator is logged in
    When the eligibility service is down and an operator chooses to pass an applicant through the eligibility check
    Then they see a technical error
    And there was a button to go back

  Scenario: Account already exists
    Given the operator is logged in
    And an account already exists for a particular NINO
    When an internal operator enters that NINO
    Then they see account already exists message