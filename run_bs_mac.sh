#!/usr/bin/env bash

export DISPLAY=${DISPLAY=":99"}

sh ./run_browser_dependencies.sh #-k=$2

# export ARGS="-Denvironment=local -Dbrowser=browserstack -Dusername=marcuscumming1 -Dkey=56HndczBWgvboCkByhta"

export ARGS="-Denvironment=local -Dbrowser=browserstack"

#sbt $ARGS -DtestDevice=BS_Sierra_Chrome_55 clean 'test-only uk.gov.hmrc.integration.cucumber.utils.RunnerBrowserStackTests'

sbt $ARGS -DtestDevice=BS_Sierra_Chrome_55 clean 'test-only htsstride.suites.RunnerSeleniumSystemTest'