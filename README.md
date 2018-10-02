[![Build Status](https://travis-ci.org/hmrc/help-to-save-stride-frontend.svg)](https://travis-ci.org/hmrc/help-to-save-stride-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/help-to-save-stride-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/help-to-save-stride-frontend/_latestVersion)

# Help To Save Stride Frontend

Stride Frontend application process for Help To Save

## Keywords

| Key | Meaning |
|:----------------:|-------------|
|DES| Data Exchange Service (Message Bus) |
|HoD| Head Of Duty, HMRC legacy application |
|HtS| Help To Save |
|MDTP| HMRC Multi-channel Digital Tax Platform |
|NS&I| National Savings & Investments |
|UC| Universal Credit|
|WTC| Working Tax Credit|



## Product Background

The Prime Minister set out the government’s intention to bring forward, a new Help to Save
(‘HtS’) scheme to encourage people on low incomes to build up a “rainy day” fund.

Help to Save will target working families on the lowest incomes to help them build up their
savings. The scheme will be open to 3.5 million adults in receipt of Universal Credit with
minimum weekly household earnings equivalent to 16 hours at the National Living Wage, or 
those in receipt of Working Tax Credit.

A customer can deposit up to a maximum of £50 per month in the account. It will work by
providing a 50% government bonus on the highest amount saved into a HtS account. The
bonus is paid after two years with an option to save for a further two years, meaning that people
can save up to £2,400 and benefit from government bonuses worth up to £1,200. Savers will be
able to use the funds in any way they wish. The published implementation date for this is Q2/2018,
but the project will have a controlled go-live with a pilot population in Q1/2018.

## Private Beta User Restriction

During Private Beta, when a HtS Account is created, per-day-count and total-count counters are 
incremented. After the customer’s Eligibility Check, the counters are checked to ensure that the 
caps haven’t been reached. If they have, they are shuttered, otherwise the operator may continue
to create a HtS account on behalf of the applicant.

Requirements
------------

This service is written in [Scala](http://www.scala-lang.org/) and [Play](http://playframework.com/), so needs at least a [JRE] to run.

## How to run

Runs on port 7006 when started locally by the service manager.

Start service manager with the following command to run the service with all required dependencies:

```
sm --start HTS_STRIDE_SELENIUM -f
```


## How to test
Selenium system tests are distinguished from unit tests by having `SeleniumSystemTest` in the relevant runner name. Note
that you will need to download Selenium drivers from http://docs.seleniumhq.org/download/. The exact version of a driver
to be downloaded will depend on the version of the corresponding browser - the versions of the driver and browser must be
compatible. Mac users will have to rename the downloaded `chromedriver` file to `chromedriver_mac`.

When testing with stride auth the stride stub will give you the option to pass in the roles you
want the stride session to have. In order to access the pages provided by help-to-save-stride-frontend
create some roles in `application.conf` under the key `stride.roles` and pass them to the stride
stub form.

The unit tests can be run by the following command:
```
sbt test
```
This command will not run the Selenium tests.

####Selenium tests
To run services locally, the required names and ports of relevant services are:

Service | Port 
|:-----------|:---------:|
help-to-save|7001
help-to-save-stub|7002
help-to-save-api|7004
help-to-save-proxy|7005
help-to-save-stride-frontend|7006
stride-auth-frontend|9041
stride-auth|9042
stride-idp-stub|9043

These can be executed with:

cd \<service path\>

sbt "run \<port\>"

Then (to run against any environment) execute:
 ```
 ./run_selenium_system_test.sh -e=${ENV} -b=${BROWSER} -d=${DRIVERS} -r=${rootUrl}
```
where `${ENV}` indicates the environment the tests should run on (one of `dev`, `qa` or `local`), `${BROWSER}` is
the browser the tests should run on `chrome` and `${DRIVERS}` is the path to the folder
containing the Selenium driver files. This command will not run the unit tests. To run only a subset of
Selenium scenarios, tag the relevant scenarios and then run the command
 ```
 ./run_selenium_system_test.sh -e=${ENV} -b=${BROWSER} -d=${DRIVERS} -r=${rootUrl} -t=${TAGS}
 ```
where `${TAGS}` is a comma separated list containing the relevant tags. Examples:

```
./run_selenium_system_test.sh -e=dev -b=chrome -d=/usr/local/bin/chromedriver -r={mdtp dev host url}                # (1) runs all selenium tests on the dev environment using chrome
./run_selenium_system_test.sh -e=qa -b=chrome -d=/usr/local/bin/chromedriver  -r={mdtp qa host url}  -t="wip"        # (2) runs selenium scenarios tagged with the `@wip` tag on the
                                                                                             #     QA environment using chrome
./run_selenium_system_test.sh -e=dev -b=chrome -d=/usr/local/bin/chromedriver -r={mdtp dev host url}  -t="wip"       # (3) the same as (2)
./run_selenium_system_test.sh -e=local -b=chrome -d=/usr/local/bin/chromedriver -t="wip,sit" # (4) runs selenium scenarios tagged with either the `@wip` or `@sit`
                                                                                             #     tags locally using chrome
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
## How to deploy

This microservice is deployed as per all MDTP microservices via Jenkins into a Docker slug running on a Cloud Provider.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
