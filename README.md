help-to-save-stride-frontend
============================
Frontend microservice which handles requests from browsers on the internal HMRC call handler journey. This journey allows
call handlers to create a HTS account for a customer over the phone.


Table of Contents
=================
* [About Help to Save](#about-help-to-save)
* [Running and Testing](#running-and-testing)
   * [Running](#running)
   * [Unit tests](#unit-tests)
* [Endpoints](#endpoints)
* [License](#license)

About Help to Save
==================
Please click [here](https://github.com/hmrc/help-to-save#about-help-to-save) for more information.

Running and Testing
===================

Running
-------

Run `sbt run` on the terminal to start the service. The service runs on port 7006 by default.
 
When running with the stub stride auth service the stride stub will give you the option to pass
in the roles you want the stride session to have. In order to access the pages provided by help-to-save-stride-frontend 
create some roles in `application.conf` under the key `stride.roles` and pass them to the stride stub form.


Unit tests                                              
----------                                              
Run `sbt test` on the terminal to run the unit tests.   


Endpoints
=========
| Path                                                        | Method | Description  |
| ------------------------------------------------------------| ------ | ------------ |
| /mdtp-internal/check-eligibility                            | GET    | Shows a page where call handlers can enter in a customer's NINO to check their eligibility for HTS|   
| /mdtp-internal/check-eligibility                            | POST   | Submits the customer's NINO to check their eligibility for HTS  |
| /mdtp-internal/customer-eligible                            | GET    | Informs the call handler that the customer is eligible if the customer is eligible for HTS |
| /mdtp-internal/customer-eligible                            | POST   | Continues on the journey from the customer-eligible page  |
| /mdtp-internal/not-eligible                                 | GET    | Informs the call handler that the customer is ineligible if the customer is ineligible for HTS  |
| /mdtp-internal/not-eligible                                 | POST   | Allows the call handler to manually override an ineligible eligibility check result and continue to create a HTS account for the customer |
| /mdtp-internal/customer-already-has-account                 | GET    | Informs the call handler that the customer already has a HTS account  |
| /mdtp-internal/create-account                               | GET    | Shows a page to the call handler to confirm the customer's details and to read the HTS terms and conditions to the customer |
| /mdtp-internal/create-account                               | POST   | Allows the call handler to create a HTS account for a customer  |
| /mdtp-internal/account-created                              | GET    | Informs the call handler that a HTS account has been created for the customer  |
| /mdtp-internal/error                                        | GET    | Shows an error page in case of a technical fault in the backend systems  |
| /mdtp-internal/application-cancelled                        | GET    | Confirms that the application has been cancelled if the call handler has elected to do so  |
| /mdtp-internal/forbidden                                    | GET    | Page shown to users who are not IP-whitelisted if IP-whitelisting is enabled  |


License
=======
This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").