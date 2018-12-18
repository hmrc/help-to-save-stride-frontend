@test
Feature: SCR personal detail validation

  Scenario: Internal operator manually creates account on behalf of eligible SCR applicant
    Given the operator is logged in with SCR clearance
    And the SCR applicant is eligible
    When the operator fills in the applicant's details
    And check the applicant's details are correct
    Then the account is successfully created

    #First name
  Scenario: First name of the customer's personal details are null
    Given the operator is logged in with SCR clearance
    And the SCR applicant is eligible
    When the operator enters the first name as null
    Then the enter a first name error message appears

    #Last name
  Scenario: Last name of the customer's personal details are over the limit
    Given the operator is logged in with SCR clearance
    And the SCR applicant is eligible
    When the operator enters a last name with over 300 characters
    Then the last name over the limit error message appears

    #dob
  Scenario: The entered date of birth of the customer is in the future
    Given the operator is logged in with SCR clearance
    And the SCR applicant is eligible
    When the operator enters a date of birth in the future
    Then the date of birth must be in the past error message appears

    #dob
  Scenario: The entered date of birth contains null and 0 values
    Given the operator is logged in with SCR clearance
    And the SCR applicant is eligible
    When the operator enters a month as 0 and the year as null
    Then the enter a date of birth error message appears

    #dob
  Scenario: The entered date of birth contains null and 0 values
    Given the operator is logged in with SCR clearance
    And the SCR applicant is eligible
    When the operator enters a day that is greater than 31 and a year earlier than 1900
    Then the enter a real date of birth error message appears

    #address line 1
  Scenario: Address line 1 of the customer's personal details are over the character limit
    Given the operator is logged in with SCR clearance
    And the SCR applicant is eligible
    When the operator enters over 30 characters for the address line 1
    Then the address line 1 error message appears

    #address line 2
  Scenario: Address line 2 of the customer's personal details is null
    Given the operator is logged in with SCR clearance
    And the SCR applicant is eligible
    When the operator enters null for the address line 2
    Then the enter an address line 2 error message appears

    #postcode
  Scenario: Postcode of the customer's personal details is over 10 characters
    Given the operator is logged in with SCR clearance
    And the SCR applicant is eligible
    When the operator enters over 10 characters for the postcode
    Then the enter a real postcode error message appears