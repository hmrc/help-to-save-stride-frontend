
Feature: Internal operator goes through the create account journey
  @check-test
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

  Scenario: Account creation fails
    Given the operator is logged in
    Given the operator does an eligibility check when NS&I is down
    When the internal operator attempts to create an account on behalf of the applicant
    Then they see a technical error
    And there was a button to go back