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
   * [Selenium tests](#selenium-tests)
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

Selenium tests
--------------
Selenium system tests are distinguished from unit tests by having `SeleniumSystemTest` in the relevant runner name. Note
that you will need to download Selenium drivers from http://docs.seleniumhq.org/download/. The exact version of a driver
to be downloaded will depend on the version of the corresponding browser - the versions of the driver and browser must be
compatible. Mac users will have to rename the downloaded `chromedriver` file to `chromedriver_mac`.


If running the Selenium tests locally, run:
```
sm --start HTS_STRIDE_SELENIUM -f

```
to run the required dependencies.

To run the selenium tests execute:
 ```
 ./run_selenium_system_test.sh -e=${ENV} -b=${BROWSER} -d=${DRIVERS} -r=${rootUrl}
```
where `${ENV}` indicates the environment the tests should run on (one of `dev`, `qa` or `local`), `${BROWSER}` is
the browser the tests should run on (e.g. `chrome`) and `${DRIVERS}` is the path to the folder
containing the Selenium driver files. This command will not run the unit tests. To run only a subset of
Selenium scenarios, tag the relevant scenarios and then run the command
 ```
 ./run_selenium_system_test.sh -e=${ENV} -b=${BROWSER} -d=${DRIVERS} -r=${rootUrl} -t=${TAGS}
 ```
where `${TAGS}` is a comma separated list containing the relevant tags. Examples:

```
# (1) runs all selenium tests on the dev environment using chrome
./run_selenium_system_test.sh \
    -e=dev \
    -b=chrome \
    -d=/usr/local/bin/chromedriver \
    -r={mdtp dev host url}

# (2) runs selenium scenarios tagged with the `@wip` tag on the QA environment using chrome                 
./run_selenium_system_test.sh \
    -e=qa \
    -b=chrome \
    -d=/usr/local/bin/chromedriver \
    -r={mdtp qa host url} \
    -t="wip"

# (3) the same as (2)        
./run_selenium_system_test.sh \
    -e=dev \
    -b=chrome \
    -d=/usr/local/bin/chromedriver \
    -r={mdtp dev host url} \
    -t="wip"       

# (4) runs selenium scenarios tagged with either the `@wip` or `@sit` tags locally using chrome
./run_selenium_system_test.sh \
    -e=local \
    -b=chrome \
    -d=/usr/local/bin/chromedriver \
    -t="wip,sit" 
```

If you wish to run the Selenium tests from Intellij, you`ll need to:
1. Install the Cucumber for Java plugin.
2. In "Edit configurations" > "Cucumber java" > "VM options" enter, for example: -Dbrowser=chrome -Denvironment=dev -Ddrivers=/usr/local/bin
3. In "Edit configurations" > "Cucumber java" > "Glue" enter: hts.steps

The shell script: 
```
run_selenium_system_test_local_chrome_service_manager.sh
```
is designed for use in the associated Jenkins build job. If running this on your local machine, note that the script 
will restart your local services upon running. At the end of the script it will then terminate all local services.

Endpoints
=========
| Path                                                        | Method | Description  |
| ------------------------------------------------------------| ------ | ------------ |
| /mdtp-internal/check-eligibility                            | GET    | Shows a page where call handlers can enter in a customer's NINO to check their eligibility for HTS|   
| /mdtp-internal/check-eligibility                            | POST   | Submits the customer's NINO to check their eligibility for HTS  |
| /mdtp-internal/customer-eligible                            | GET    | Informs the call handler that the customer is eligible is the customer is eligible for HTS |
| /mdtp-internal/customer-eligible                            | POST   | Continues on the journey from the customer-eligible page  |
| /mdtp-internal/not-eligible                                 | GET    | Informs the call handler that the customer is ineligible is the customer is ineligible for HTS  |
| /mdtp-internal/not-eligible                                 | POST   | Allows the call handler to manually override an ineligible eligibility check result and continue to create a HTS for the customer |
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
