@quotes
Feature: Make sure all pages use smart quotes only

  Scenario: Check that the check eligibility page uses smart quotes only
    Given the operator is logged in
    When the operator navigates to the /check-eligibility page
    Then they see that the page has only smart quotes

  Scenario: Check that the customer eligible page uses smart quotes only
    Given the operator is logged in
    When the operator navigates to the /customer-eligible page
    Then they see that the page has only smart quotes

  Scenario: Check that the account created page uses smart quotes only
    Given the operator is logged in
    When the operator navigates to the /account-created page
    Then they see that the page has only smart quotes

  Scenario: Check that the customer already has account page uses smart quotes only
    Given the operator is logged in
    When the operator navigates to the /customer-already-has-account page
    Then they see that the page has only smart quotes