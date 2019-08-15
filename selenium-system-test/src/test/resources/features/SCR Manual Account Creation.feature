@test
Feature: SCR Manual Account Creation

  Scenario Outline: Call handler creates account
    Given the operator is logged with SCR clearance <role>
    And the SCR applicant is ineligible
    And the operator ticks the 'I confirm I have met these conditions' button
    When the operator fills in the ineligible applicant's details
    And verifies the applicant's details are correct
    Then the account is successfully created
    Examples:
      | role                        |
      | hts helpdesk advisor secure |
      | hts_helpdesk_advisor_secure |

  Scenario: Invalid inputs
    Given the operator is logged with SCR clearance
    And the SCR applicant is ineligible
    And the operator ticks the 'I confirm I have met these conditions' button
    When the operator enters invalid input
    Then the appropriate error messages appear