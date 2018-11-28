Feature: Operator manually creates account on behalf of an applicant

Scenario: Internal operator manually creates account on behalf of ineligible applicant
Given the operator is logged in
And the applicant has NINO WP100001C
When the internal operator does an eligibility check on behalf of the applicant
Then they see that the applicant is NOT eligible for Help to Save with reason code 5
Given the operator has evidence the applicant is eligible for a Help to Save account
When the internal operator chooses to create an account manually on behalf of the applicant
Then the account is successfully created